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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.h2.jdbcx.JdbcDataSource;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedPushTask implements Runnable {

  private static Logger logger = LoggerFactory.getLogger(FeedPushTask.class);

  private final Whitelist whitelist = (new Whitelist()).addTags("p")
      .removeAttributes("p", "style", "align");

  private final FeedPushConfig config;
  private final CloseableHttpClient client;

  private URI uri;
  private DaDaPushMessageApi api;
  private JdbcDataSource dataSource;

  public FeedPushTask(FeedPushConfig config, CloseableHttpClient client)
      throws URISyntaxException, SQLException {
    this.config = config;
    this.client = client;
    init();
  }

  private void init() throws URISyntaxException, SQLException {
    logger.info("process feed[{}] db[{}]", config.getFeedUrl(), config.getDatabasePath());
    uri = new URI(config.getFeedUrl());
    api = new DaDaPushMessageApi();
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:" + config.getDatabasePath() + ";FILE_LOCK=FS" +
        ";PAGE_SIZE=1024" +
        ";CACHE_SIZE=8192");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    QueryRunner queryRunner = new QueryRunner(dataSource);
    queryRunner.execute(
        "CREATE TABLE IF NOT EXISTS feed_cache(ID INT PRIMARY KEY auto_increment, title VARCHAR(255) not null, md5title VARCHAR(32) not null unique,url VARCHAR(255) not null,description TEXT,publishedDate datetime);");
    queryRunner.execute("ALTER TABLE feed_cache CHANGE url url VARCHAR(1000) NOT NULL DEFAULT '';");
    queryRunner
        .execute("ALTER TABLE feed_cache CHANGE title title VARCHAR(1000) NOT NULL DEFAULT '';");
  }

  private boolean isCache(String md5title) throws SQLException {
    QueryRunner queryRunner = new QueryRunner(dataSource);
    FeedInfo feedInfo = queryRunner
        .query("select * from feed_cache where md5title=?", resultSet -> {
          if (!resultSet.next()) {
            return null;
          }
          FeedInfo feedInfo1 = new FeedInfo();
          feedInfo1.setTitle(resultSet.getString("title"));
          feedInfo1.setDescription(resultSet.getString("description"));
          feedInfo1.setUrl(resultSet.getString("url"));
          feedInfo1.setPublishedDate(resultSet.getDate("publishedDate"));
          return feedInfo1;
        }, md5title);
    return feedInfo != null;
  }

  private boolean addCache(String md5title, FeedInfo feedInfo) throws SQLException {
    QueryRunner queryRunner = new QueryRunner(dataSource);
    int update = queryRunner
        .update(
            "insert into feed_cache(title,md5title,url,description,publishedDate) values(?,?,?,?,?)",
            feedInfo.getTitle(), md5title, feedInfo.getUrl(), feedInfo.getDescription(),
            feedInfo.getPublishedDate());
    return update > 0;
  }

  private SyndFeed fetchFeed(URI uri) throws IOException, FeedException {
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

  private List<FeedInfo> parseFeedInfo(SyndFeed feed) {
    List<FeedInfo> feedInfoList = new ArrayList<>();
    List feedEntries = feed.getEntries();
    for (Object object : feedEntries) {
      SyndEntryImpl syndEntry = (SyndEntryImpl) object;
      String title = syndEntry.getTitle();
      if (StringUtils.isEmpty(title)) {
        continue;
      }
      String link = syndEntry.getLink();
      if (StringUtils.isEmpty(link)) {
        continue;
      }
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
      if (StringUtils.isEmpty(raw_description)) {
        continue;
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
      SyndFeed syndFeed = fetchFeed(uri);
      List<FeedInfo> feedInfoList = parseFeedInfo(syndFeed);
      for (FeedInfo feedInfo : feedInfoList) {
        String md5title = DigestUtils.md5Hex(feedInfo.getUrl());
        try {
          boolean cached = isCache(md5title);
          logger.debug("{} cached={}", feedInfo.getTitle(), cached);

          if (!cached) {
            sendPush(md5title, feedInfo);
          }
        } catch (SQLException e) {
          logger.error("process feedInfo error, feedInfo={}", feedInfo, e);
        }
      }

    } catch (IOException | FeedException e) {
      logger.error("process uri error, uri={}", uri, e);
    }
  }

  private void sendPush(String md5title, FeedInfo feedInfo) {
    MessagePushRequest body = new MessagePushRequest();
    body.setNeedPush(true);
    if (StringUtils.isEmpty(feedInfo.getTitle()) || StringUtils
        .isEmpty(feedInfo.getDescription())) {
      return;
    }
    String title = StringUtils.truncate(Jsoup.clean(feedInfo.getTitle(), Whitelist.none()), 50);
    body.setTitle(title);
    feedInfo.setTitle(title);
    String content = StringUtils.truncate(feedInfo.getDescription(), 500 - 3) + "...";
    feedInfo.setDescription(content);
    body.setContent(content);
    Action action = new Action();
    action.setUrl(feedInfo.getUrl());
    action.setType(TypeEnum.LINK);
    action.setName("VIEW");
    body.setActions(Collections.singletonList(action));
    try {
      ResultOfMessagePushResponse result = api.createMessage(body, config.getChannelToken());
      if (0 == result.getCode()) {
        logger.info("send push success, title={} result={}", feedInfo.getTitle(), result);
        try {
          boolean addCache = addCache(md5title, feedInfo);
          logger.info("add feed to cache, title={} insert={}", feedInfo.getTitle(), addCache);
        } catch (SQLException e) {
          logger.info("add feed to cache error, title={}", feedInfo.getTitle(), e);
        }
      } else if (206 == result.getCode()) {
        try {
          Thread.sleep(config.getSleepTime());// avoid too many request
          sendPush(md5title, feedInfo);
        } catch (InterruptedException e) {
          logger.info("sleep error, title={}", feedInfo.getTitle(), e);
        }
      } else {
        logger.warn("send push fail, title={} result={}", feedInfo.getTitle(), result);
      }

    } catch (ApiException e) {
      logger.info("send push fail, feedInfo={}", feedInfo, e);
    }
  }
}
