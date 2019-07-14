package com.dadapush.feed;

import java.util.Date;

class FeedInfo {

  private String title;
  private String url;
  private String description;
  private Date publishedDate;

  public FeedInfo(String title, String url, String description, Date publishedDate) {
    this.title = title;
    this.url = url;
    this.description = description;
    this.publishedDate = publishedDate;
  }

  public FeedInfo() {
  }

  public Date getPublishedDate() {
    return publishedDate;
  }

  public void setPublishedDate(Date publishedDate) {
    this.publishedDate = publishedDate;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FeedInfo feedInfo = (FeedInfo) o;

    if (title != null ? !title.equals(feedInfo.title) : feedInfo.title != null) {
      return false;
    }
    return url != null ? url.equals(feedInfo.url) : feedInfo.url == null;
  }

  @Override
  public int hashCode() {
    int result = title != null ? title.hashCode() : 0;
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "FeedInfo{" +
        "title='" + title + '\'' +
        ", url='" + url + '\'' +
        ", description='" + description + '\'' +
        ", publishedDate=" + publishedDate +
        '}';
  }
}
