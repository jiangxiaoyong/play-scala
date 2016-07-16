package controllers

import java.io.File
import javax.inject.Inject

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.{ListBuffer, Queue}

class Application @Inject()(ws: WSClient) extends Controller {

  //check that server is running
  def index = Action {
    Ok("Server is running : )")
  }

  // sends the time every second, ignores any input
  def wsTime = WebSocket.using[String] {
    request =>
      Logger.info(s"wsTime, client connected.")
      new KafkaConsumer("192.168.99.100:2181","1","sentiment",10).run
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
  def wsEcho = WebSocket.using[String] {
    request => {
      Logger.info(s"wsEcho, client connected.")
      var channel: Option[Concurrent.Channel[String]] = None
      val outEnumerator: Enumerator[String] = Concurrent.unicast(c => channel = Some(c))

      val inIteratee: Iteratee[String, Unit] = Iteratee.foreach[String](receivedString => {
        // send string back
        Logger.info(s"wsEcho, received: $receivedString")
        channel.foreach(_.push(receivedString))
      })

      (inIteratee, outEnumerator)
    }
  }

  def socket = WebSocket.using[String] { request =>

    // Concurrent.broadcast returns (Enumerator, Concurrent.Channel)
    val (out, channel) = Concurrent.broadcast[String]

    // log the message to stdout and send response back to client
    val in = Iteratee.foreach[String] {
      msg => println(msg)
        // the Enumerator returned by Concurrent.broadcast subscribes to the channel and will
        // receive the pushed messages
        channel push("I received your message: " + msg)
    }
    (in,out)
  }
}

class KafkaConsumer (val zookeeper: String,
                     val groupId: String,
                     val topic: String,
                     val delay: Long) extends Logging {
  val config = createKafkaConsumerConfig(zookeeper, groupId)
  val consumer = Consumer.create(config)
  var pool: ExecutorService = Executors.newFixedThreadPool(2)

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

