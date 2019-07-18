# dadapush-feed
send RSS or Atom feeds to DaDaPush

DaDaPush: Real-time Notifications App

Send real-time notifications through our API without coding and maintaining your own app for iOS or Android devices.

# Usage
```
  usage: java -jar dadapush-feed-[VERSION]-jar-with-dependencies.jar
   -d,--debug           turn on debug mode
   -h,--help            print help message
   -p,--path <path>     database path
   -T,--token <token>   channel token
   -u,--url <url>       feed url
```

## manual:
```
java -jar dadapush-feed-1.0.0-jar-with-dependencies.jar -p "[YOUR_FILE_PATH]/db" -T YOUR_TOKEN -u [YOUR_FEED_URL]
```

## crontab:
```
*/5 * * * * java -jar dadapush-feed-1.0.0-jar-with-dependencies.jar -p "[YOUR_FILE_PATH]/db" -T YOUR_TOKEN -u [YOUR_FEED_URL]
```

