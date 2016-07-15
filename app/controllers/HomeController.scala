package controllers

import javax.inject._

import kafka.consumer.{Consumer, ConsumerConfig}
import kafka.utils.Logging
import play.api._
import play.api.mvc._
import java.util.Properties

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    val kafka =  new KafkaConsumer("192.168.99.100:2181","1","sentiment",10)
    kafka.run
    Ok(views.html.index("Your new application is ready."))
  }

}

class KafkaConsumer (val zookeeper: String,
                     val groupId: String,
                     val topic: String,
                     val delay: Long) extends Logging {
  val config = createKafkaConsumerConfig(zookeeper, groupId)
  val consumer = Consumer.create(config)

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
      val it = stream.iterator()
      while (it.hasNext()) {
        val msg = new String(it.next().message());
        System.out.println(System.currentTimeMillis() + "msg: " + msg);
      }
    }
  }
}
