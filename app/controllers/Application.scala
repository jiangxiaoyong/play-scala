package controllers

import java.io.File
import javax.inject.Inject

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import play.api.Play.current
import play.api._
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{Concurrent, Enumerator, Iteratee}
import play.api.libs.ws._
import play.api.mvc._
import kafka.consumer.{Consumer, ConsumerConfig, KafkaStream}
import kafka.utils.Logging
import java.util.Properties
import java.util.concurrent.{ExecutorService, Executors}

import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import play.api.libs.streams.ActorFlow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.{ListBuffer, Queue}

class Application @Inject()(implicit system: ActorSystem, materializer: Materializer) extends Controller {

  //check that server is running
  def index = Action {
    Ok(views.html.index("Twitter Sentiment Analysis"))
  }

  // sends the time every second, ignores any input
  def wsTime = WebSocket.using[String] {
    request =>
      Logger.info(s"wsTime, client connected.")
      new KafkaConsumer("192.168.99.100:2181","1","sentiment").run
      val outEnumerator: Enumerator[String] = Enumerator.repeatM(Promise.timeout({
        val listBuffer = new ListBuffer[String]
        for(i <- 1 to 1000) {
          if(!msgQueue.queue.isEmpty) listBuffer += msgQueue.readQueue
        }
        listBuffer.toString()
      }, 100))
      val inIteratee: Iteratee[String, Unit] = Iteratee.ignore[String]

      (inIteratee, outEnumerator)
  }

  // endpoint that opens an echo websocket
  def wsEcho = WebSocket.accept[String, String] {
    request => {
      Logger.info(s"wsEcho, client connected.")
      Flow[String].map(msg => "I received your message: " + msg)
    }
  }

  //endpoint that send streaming data buffered in queue to client
  def socket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef(out => MyWebSocketActor.props(out))
  }
}

object MyWebSocketActor {
  def props(out: ActorRef) = Props(new MyWebSocketActor(out))
}

/**
  * Describe how to response msg received from client
  *
  * @param out
  */
class MyWebSocketActor(out: ActorRef) extends Actor {
  def receive = {
    case msg: String =>
      Logger.info(s"actor, received message: $msg")
      if (msg == "bye") self ! PoisonPill // closing WebSocket when receive "bye" msg from client
      else if ( msg == "start") new KafkaConsumer("192.168.99.100:2181","1","sentiment").run
      else {
         while(!msgQueue.queue.isEmpty) {
            out ! (msgQueue.readQueue)
        }
      }
  }
}

/**
  * An Kafka consumer which will read data from Kafka about specific topic
  *
  * @param zookeeper
  * @param groupId
  * @param topic
  */
class KafkaConsumer (val zookeeper: String,
                     val groupId: String,
                     val topic: String) extends Logging {
  val config = createKafkaConsumerConfig(zookeeper, groupId)
  val consumer = Consumer.create(config)
  var pool: ExecutorService = Executors.newFixedThreadPool(1)

  def createKafkaConsumerConfig(zookeeper: String, groupId: String): ConsumerConfig = {
    val props = new Properties()
    props.put("zookeeper.connect", zookeeper);
    props.put("group.id", groupId);
    props.put("auto.offset.reset", "largest");
    props.put("zookeeper.session.timeout.ms", "400");
    props.put("zookeeper.sync.time.ms", "200");
    props.put("auto.commit.interval.ms", "1000");
    val config = new ConsumerConfig(props)
    config
  }

  def run = {
    val topicCountMap = Map(topic -> 1)
    val consumerMap = consumer.createMessageStreams(topicCountMap)
    val streams = consumerMap.get(topic).get

    for (stream <- streams) {
      pool.submit(new Buffer(stream)) //fork new thread to buffer kafka streaming data
    }
  }
}

/**
  * Buffer all data fetched from a kafka topic into queue, and the data in this queue will be send over WebSocket
  *
  * @param stream
  */
class Buffer(val stream: KafkaStream[Array[Byte], Array[Byte]]) extends Logging with Runnable {
  def run = {
    val it = stream.iterator()

    while (it.hasNext()) {
      val msg = new String(it.next().message())
      msgQueue.writeQueue(msg)
      Logger.info("queue size " + msgQueue.queue.size)
      Logger.info(System.currentTimeMillis() + "msg: " + msg)
    }
  }
}

object msgQueue {
  val queue = new Queue[String]

  def writeQueue(s: String) = {
    queue.enqueue(s)
  }

  def readQueue: String = {
    queue.dequeue
  }
}

