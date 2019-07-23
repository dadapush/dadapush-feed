package com.dadapush.feed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

public class ConvertUtil {

  public static void main(String[] args)
      throws IOException {

    BufferedReader reader = Files
        .newBufferedReader(Paths.get("/Users/ysykzheng/repo/dadapush/dadapush-feed-data/all.sh"));

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
    List<FeedPushConfig> feedPushConfigList = reader.lines().map(s -> {
      String s1 = s.substring(s.indexOf("dadapush-feed-1.0.0-jar-with-dependencies.jar "));

      try {
        CommandLine commandLine = parser.parse(options, s1.split(" "));
        String token = commandLine.getOptionValue("token");
        String path = commandLine.getOptionValue("path");
        String url = commandLine.getOptionValue("url");
        System.out.println(token);
        System.out.println(path);
        System.out.println(url);
        FeedPushConfig feedPushConfig = new FeedPushConfig(token, path, url);
        return feedPushConfig;
      } catch (ParseException e) {
        return new FeedPushConfig();
      }
    }).filter(feedPushConfig -> StringUtils.isNotEmpty(feedPushConfig.getChannelToken()))
        .collect(Collectors.toList());
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    FileWriter fileWriter = new FileWriter(new File("config.json"));
    gson.toJson(feedPushConfigList, fileWriter);
    fileWriter.flush();
    fileWriter.close();
  }
}
