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

  private static Logger logger = LoggerFactory.getLogger(FeedPushManager.class);

  public static void main(String[] args)
      throws URISyntaxException, SQLException, IOException, ParseException {

    Options options = new Options();
    options.addOption(Option.builder("c").argName("config.json")
        .longOpt("config")
        .desc("config file")
        .hasArg(true)
        .numberOfArgs(1)
        .type(String.class)
        .build());

    options.addOption(Option.builder("T").argName("token")
        .longOpt("token")
        .desc("channel token")
        .hasArg(true)
        .numberOfArgs(1)
        .type(String.class)
        .build());

    options.addOption(Option.builder("p").argName("path")
        .longOpt("path")
        .desc("database path")
        .hasArg(true)
        .numberOfArgs(1)
        .type(String.class)
        .build());

    options.addOption(Option.builder("u").argName("url")
        .longOpt("url")
        .desc("feed url")
        .hasArg(true)
        .numberOfArgs(1)
        .type(String.class)
        .build());

    options.addOption(Option.builder("n").argName("300ms")
        .longOpt("interval")
        .desc("interval time, default 300ms")
        .hasArg(true)
        .numberOfArgs(1)
        .type(Long.class)
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

    final RequestConfig requestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(5000)
        .setSocketTimeout(5000)
        .setConnectionRequestTimeout(5000)
        .setContentCompressionEnabled(true)
        .setMaxRedirects(5)
        .build();

    final CloseableHttpClient client = HttpClients.custom()
        .setUserAgent("dadapush-feed/1.0.0")
        .setDefaultRequestConfig(requestConfig)
        .build();

    if (cmd.hasOption("d")) {
      System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
    } else {
      System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
    }
    if (cmd.hasOption("config")) {
      String configFile = cmd.getOptionValue("config");
      run(configFile, client);
    } else if (cmd.hasOption("token") && cmd.hasOption("path") && cmd.hasOption("url")) {
      FeedPushConfig feedPushConfig = new FeedPushConfig(cmd.getOptionValue("token")
          , cmd.getOptionValue("path"), cmd.getOptionValue("url"),
          Long.valueOf(cmd.getOptionValue("interval", "300")));

      if (StringUtils.isEmpty(feedPushConfig.getChannelToken())) {
        throw new RuntimeException("channelToken is empty. " + feedPushConfig);
      }
      if (StringUtils.isEmpty(feedPushConfig.getFeedUrl())) {
        throw new RuntimeException("feedUrl is empty. " + feedPushConfig);
      }
      if (StringUtils.isEmpty(feedPushConfig.getDatabasePath())) {
        throw new RuntimeException("databasePath is empty. " + feedPushConfig);
      }
      runSimple(feedPushConfig, client);
    } else {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("java -jar dadapush-feed-[VERSION]-jar-with-dependencies.jar\n"
          + "If the option [--config/-c] is set, it will be used first.", options);
    }

  }

  private static void runSimple(FeedPushConfig feedPushConfig, CloseableHttpClient client)
      throws URISyntaxException, SQLException, IOException {
    FeedPushTask feedPushTask = new FeedPushTask(feedPushConfig, client);
    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    ForkJoinTask<?> task = forkJoinPool.submit(feedPushTask);
    task.join();
    client.close();
  }

  private static void run(String configFile, CloseableHttpClient client)
      throws URISyntaxException, SQLException, IOException {
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
    if (processors < 2) {
      processors = 2;
    }
    ForkJoinPool forkJoinPool = new ForkJoinPool(processors);
    List<ForkJoinTask> forkJoinTaskList = new ArrayList<>();

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
      ForkJoinTask<?> task = forkJoinPool.submit(new FeedPushTask(feedPushConfig, client));
      forkJoinTaskList.add(task);
    }

    for (ForkJoinTask task : forkJoinTaskList) {
      task.join();
    }
    client.close();
  }
}
