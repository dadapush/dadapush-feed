package com.dadapush.feed;


public class FeedPushConfig {

  private String channelToken;
  private String databasePath;
  private String feedUrl;
  private Long sleepTime=300L;

  public FeedPushConfig(String channelToken, String databasePath, String feedUrl,
      Long sleepTime) {
    this.channelToken = channelToken;
    this.databasePath = databasePath;
    this.feedUrl = feedUrl;
    this.sleepTime = sleepTime;
  }

  public FeedPushConfig() {
  }

  public FeedPushConfig(String channelToken, String databasePath, String feedUrl) {
    this.channelToken = channelToken;
    this.databasePath = databasePath;
    this.feedUrl = feedUrl;
  }


  public String getChannelToken() {
    return channelToken;
  }

  public void setChannelToken(String channelToken) {
    this.channelToken = channelToken;
  }

  public String getDatabasePath() {
    return databasePath;
  }

  public void setDatabasePath(String databasePath) {
    this.databasePath = databasePath;
  }

  public String getFeedUrl() {
    return feedUrl;
  }

  public void setFeedUrl(String feedUrl) {
    this.feedUrl = feedUrl;
  }

  public Long getSleepTime() {
    return sleepTime;
  }

  public void setSleepTime(Long sleepTime) {
    this.sleepTime = sleepTime;
  }

  @Override
  public String toString() {
    return "FeedPushConfig{" +
        "channelToken='" + channelToken + '\'' +
        ", databasePath='" + databasePath + '\'' +
        ", feedUrl='" + feedUrl + '\'' +
        ", sleepTime=" + sleepTime +
        '}';
  }
}
