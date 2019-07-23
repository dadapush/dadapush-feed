package com.dadapush.feed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedPushManager {

  private static Logger logger = LoggerFactory.getLogger(FeedPushRunner.class);

  public static void main(String[] args)
      throws URISyntaxException, SQLException, IOException, ParseException {

    Options options = new Options();
    options.addOption(Option.builder("c").argName("config")
        .longOpt("config")
        .desc("config file")
        .hasArg(true)
        .numberOfArgs(1)
        .type(String.class)
        .build());


    options.addOption(Option.builder("d").argName("debug")
        .longOpt("debug")
        .desc("turn on debug mode")
        .hasArg(false)
        .build());

    options.addOption(Option.builder("h").argName("help")
        .longOpt("help")
        .desc("print help message")
        .hasArg(false)
        .build());

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    if (cmd.hasOption("d")) {
      System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
    } else {
      System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
    }
    if (cmd.hasOption("config")) {
      String configFile = cmd.getOptionValue("config");
      run(configFile);
    }else {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("java -jar dadapush-feed-[VERSION]-jar-with-dependencies.jar", options);
    }

  }

  private static void run(String configFile) throws URISyntaxException, SQLException, IOException {
    File file = new File(configFile);
    if (!file.exists() || !file.canRead()) {
      logger.error("file[] not exists or can't read", file);
      return;
    }
    Gson gson = new GsonBuilder().create();
    List<FeedPushConfig> feedPushConfigList = gson
        .fromJson(new FileReader(file), new TypeToken<List<FeedPushConfig>>() {
        }.getType());

    int processors = Runtime.getRuntime().availableProcessors();
    if(processors<2){
      processors=2;
    }
    ForkJoinPool forkJoinPool = new ForkJoinPool(processors);
    List<ForkJoinTask> forkJoinTaskList = new ArrayList<>();

    final RequestConfig requestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(3000)
        .setSocketTimeout(3000)
        .setConnectionRequestTimeout(3000)
        .setContentCompressionEnabled(true)
        .setMaxRedirects(3)
        .build();

    final CloseableHttpClient client = HttpClients.custom()
        .setUserAgent("dadapush-feed/1.0.0")
        .setDefaultRequestConfig(requestConfig)
        .build();
    for (FeedPushConfig feedPushConfig : feedPushConfigList) {
      if (StringUtils.isEmpty(feedPushConfig.getChannelToken())) {
        throw new RuntimeException("channelToken is empty. " + feedPushConfig);
      }
      if (StringUtils.isEmpty(feedPushConfig.getFeedUrl())) {
        throw new RuntimeException("feedUrl is empty. " + feedPushConfig);
      }
      if (StringUtils.isEmpty(feedPushConfig.getDatabasePath())) {
        throw new RuntimeException("databasePath is empty. " + feedPushConfig);
      }
      if (feedPushConfig.getSleepTime() < 300L) {
        throw new RuntimeException("sleepTime not allow less than 500ms. " + feedPushConfig);
      }
      ForkJoinTask<?> task = forkJoinPool.submit(new FeedPushTask(feedPushConfig,client));
      forkJoinTaskList.add(task);
    }

    for (ForkJoinTask task : forkJoinTaskList) {
      task.join();
    }
    client.close();
  }
}
