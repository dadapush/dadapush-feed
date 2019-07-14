package com.dadapush.feed;

import com.dadapush.client.ApiException;
import com.dadapush.client.api.DaDaPushMessageApi;
import com.dadapush.client.model.Action;
import com.dadapush.client.model.Action.TypeEnum;
import com.dadapush.client.model.MessagePushRequest;
import com.dadapush.client.model.ResultOfMessagePushResponse;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.h2.jdbcx.JdbcDataSource;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedPush implements Closeable, Runnable {

  private static Logger logger = LoggerFactory.getLogger(FeedPush.class);
  private Whitelist whitelist = (new Whitelist()).addTags("p")
      .removeAttributes("p", "style", "align");
  private CloseableHttpClient client;
  private URI uri;
  private String X_CHANNEL_TOKEN;
  private DaDaPushMessageApi api;
  private JdbcDataSource dataSource;
  private int SLEEP_TIME = 1500;

  public static void main(String[] args)
      throws URISyntaxException, SQLException, ParseException {

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

    if (cmd.hasOption("token") && cmd.hasOption("path") && cmd.hasOption("url")) {
      String token = cmd.getOptionValue("token");
      String path = cmd.getOptionValue("path");
      String url = cmd.getOptionValue("url");

      FeedPush feedPush = new FeedPush(
          token, path, url
      );
      if (cmd.hasOption("d")) {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
      } else {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
      }
      feedPush.run();
      feedPush.close();
    } else {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("java -jar dadapush-feed-[VERSION]-jar-with-dependencies.jar", options);
    }


  }

  public FeedPush(String token, String dbPath, String url)
      throws URISyntaxException, SQLException {
    uri = new URI(url);
    X_CHANNEL_TOKEN = token;
    api = new DaDaPushMessageApi();
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:" + dbPath + ";FILE_LOCK=FS" +
        ";PAGE_SIZE=1024" +
        ";CACHE_SIZE=8192");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    QueryRunner queryRunner = new QueryRunner(dataSource);
    queryRunner.execute(
        "CREATE TABLE IF NOT EXISTS feed_cache(ID INT PRIMARY KEY auto_increment, title VARCHAR(255) not null, md5title VARCHAR(32) not null unique,url VARCHAR(255) not null,description TEXT,publishedDate datetime);");

    RequestConfig requestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(3000)
        .setSocketTimeout(3000)
        .setConnectionRequestTimeout(3000)
        .setContentCompressionEnabled(true)
        .setMaxRedirects(3)
        .build();

    client = HttpClients.custom()
        .setUserAgent("dadapush-feed/1.0.0")
        .setDefaultRequestConfig(requestConfig)
        .build();
  }

  public void close() {
    try {
      client.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public boolean isCache(String md5title) throws SQLException {
    QueryRunner queryRunner = new QueryRunner(dataSource);
    FeedInfo feedInfo = queryRunner
        .query("select * from feed_cache where md5title=?", new ResultSetHandler<FeedInfo>() {
          @Override
          public FeedInfo handle(ResultSet resultSet) throws SQLException {
            if (!resultSet.next()) {
              return null;
            }
            FeedInfo feedInfo = new FeedInfo();
            feedInfo.setTitle(resultSet.getString("title"));
            feedInfo.setDescription(resultSet.getString("description"));
            feedInfo.setUrl(resultSet.getString("url"));
            feedInfo.setPublishedDate(resultSet.getDate("publishedDate"));
            return feedInfo;
          }
        }, md5title);
    return feedInfo != null;
  }

  public boolean addCache(String md5title, FeedInfo feedInfo) throws SQLException {
    QueryRunner queryRunner = new QueryRunner(dataSource);
    int update = queryRunner
        .update(
            "insert into feed_cache(title,md5title,url,description,publishedDate) values(?,?,?,?,?)",
            feedInfo.getTitle(), md5title, feedInfo.getUrl(), feedInfo.getDescription(),
            feedInfo.getPublishedDate());
    return update > 0;
  }

  public SyndFeed fetchFeed(URI uri) throws IOException, FeedException {
    HttpUriRequest request = new HttpGet(uri);
    CloseableHttpResponse response = client.execute(request);
    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() >= 400) {
      throw new IOException(statusLine.toString());
    }
    HttpEntity httpEntity = response.getEntity();
    InputStream stream = httpEntity.getContent();
    SyndFeedInput input = new SyndFeedInput();
    SyndFeed feed = input.build(new XmlReader(stream));
    stream.close();
    response.close();
    return feed;
  }

  public List<FeedInfo> parseFeedInfo(SyndFeed feed) {
    List<FeedInfo> feedInfoList = new ArrayList<>();
    List feedEntries = feed.getEntries();
    for (Object object : feedEntries) {
      SyndEntryImpl syndEntry = (SyndEntryImpl) object;
      String title = syndEntry.getTitle();
      String link = syndEntry.getLink();
      Date publishedDate = syndEntry.getPublishedDate();
      SyndContent syndContent = syndEntry.getDescription();
      String raw_description = null;
      if (syndContent == null) {
        List contents = syndEntry.getContents();
        if (contents != null) {
          for (Object content : contents) {
            SyndContent syndContent1 = (SyndContent) content;
            raw_description = syndContent1.getValue();
            break;
          }
        }
      } else {
        raw_description = syndContent.getValue();
      }
      String description = Jsoup.clean(raw_description, whitelist);
      description = description.replace("<p>", "");
      description = description.replace("</p>", "");
      description = description.replace("&nbsp;", "");

      FeedInfo feedInfo = new FeedInfo(title, link, description, publishedDate);
      logger.debug("feedInfo={}", feedInfo);
      feedInfoList.add(feedInfo);
    }
    return feedInfoList;
  }

  @Override
  public void run() {
    try {
      Calendar instance = Calendar.getInstance();
      instance.setTime(new Date());
      instance.add(Calendar.DAY_OF_YEAR, -3);
      SyndFeed syndFeed = fetchFeed(uri);
      List<FeedInfo> feedInfoList = parseFeedInfo(syndFeed);
      for (FeedInfo feedInfo : feedInfoList) {
        if (instance.before(feedInfo.getPublishedDate())) {
          logger.info("expired feed. title={} publishedDate={}", feedInfo.getTitle(),
              feedInfo.getPublishedDate());
        } else {
          String md5title = DigestUtils.md5Hex(feedInfo.getUrl());
          try {
            boolean cached = isCache(md5title);
            logger.debug("{} cached={}", feedInfo.getTitle(), cached);

            if (!cached) {
              sendPush(md5title, feedInfo);
            }
            if (SLEEP_TIME > 1) {
              Thread.sleep(SLEEP_TIME);// too many request
            }
          } catch (SQLException | InterruptedException e) {
            logger.error("process feedInfo error, feedInfo={}", feedInfo, e);
          }
        }
      }

    } catch (IOException | FeedException e) {
      logger.error("process uri error, uri={}", uri, e);
    }
  }

  public void sendPush(String md5title, FeedInfo feedInfo) {
    MessagePushRequest body = new MessagePushRequest();
    body.setNeedPush(true);
    body.setTitle(StringUtils.truncate(Jsoup.clean(feedInfo.getTitle(), Whitelist.none()), 50));
    body.setContent(StringUtils.truncate(feedInfo.getDescription(), 500 - 3) + "...");
    Action action = new Action();
    action.setUrl(feedInfo.getUrl());
    action.setType(TypeEnum.LINK);
    action.setName("VIEW");
    body.setActions(Collections.singletonList(action));
    try {
      ResultOfMessagePushResponse result = api.createMessage(body, X_CHANNEL_TOKEN);
      if (result.getCode() == 0) {
        logger.info("send push success, title={} result={}", feedInfo.getTitle(), result);
      } else {
        logger.warn("send push fail, title={} result={}", feedInfo.getTitle(), result);
      }
      try {
        boolean addCache = addCache(md5title, feedInfo);
        logger.info("add feed to cache, title={} insert={}", feedInfo.getTitle(), addCache);
      } catch (SQLException e) {
        logger.info("add feed to cache error, title={}", feedInfo.getTitle(), e);
      }
    } catch (ApiException e) {
      logger.info("send push fail, feedInfo={}", feedInfo, e);
    }
  }
}
