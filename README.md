Docker Instructions
================================
star new container
--------------------------------
```
docker run -it --name playScala --expose 9000 --expose 9090 -p 9000:9000 -p 9090:9090 -v ~/IdeaProjects/play-scala/:/app jiangxiaoyong/play-scala
```
- port 9000 is server listening port
- port 9090 is debugging port

Running instructions
===============================
Activator
-------------------------------
- use activator to run play application
```
$ activator run
```
Fetch data from play WebSocket
------------------------------
### In the root path of web app
- connect to WebSocket of play server
- send 'start' message to server to trigger running of Kafka consumer which consume message of specific topic
- send any message to receive data

How to run entire Twitter Streaming Sentiment Analysis
======================================================
- please see instructions of another git repo to
[Running the whole app](https://github.com/jiangxiaoyong/StreamingTwitterSentimentAnalysis)


