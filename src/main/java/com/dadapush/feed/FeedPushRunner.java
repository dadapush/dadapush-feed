package com.dadapush.feed;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
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

public class FeedPushRunner {

  public static void main(String[] args)
      throws URISyntaxException, SQLException, ParseException, IOException {

    Options options = new Options();
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

    if (cmd.hasOption("token") && cmd.hasOption("path") && cmd.hasOption("url")) {
      FeedPushConfig feedPushConfig = new FeedPushConfig(cmd.getOptionValue("token")
          , cmd.getOptionValue("path"), cmd.getOptionValue("url"), 300L);

      if (StringUtils.isEmpty(feedPushConfig.getChannelToken())) {
        throw new RuntimeException("channelToken is empty. " + feedPushConfig);
      }
      if (StringUtils.isEmpty(feedPushConfig.getFeedUrl())) {
        throw new RuntimeException("feedUrl is empty. " + feedPushConfig);
      }
      if (StringUtils.isEmpty(feedPushConfig.getDatabasePath())) {
        throw new RuntimeException("databasePath is empty. " + feedPushConfig);
      }

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

      FeedPushTask feedPushTask = new FeedPushTask(feedPushConfig, client);
      ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
      ForkJoinTask<?> task = forkJoinPool.submit(feedPushTask);
      task.join();
      client.close();
    } else {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("java -cp dadapush-feed-[VERSION]-jar-with-dependencies.jar com.dadapush.feed.FeedPushRunner", options);
    }
  }
}
