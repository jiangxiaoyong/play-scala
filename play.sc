import scala.collection.mutable.{ListBuffer, Queue}

object msgQueue {
  val queue = new Queue[String]

  def writeQueue(s: String) = {
    queue.enqueue(s)
  }

  def readQueue: String = {
    queue.dequeue
  }
}

for( i <- 1 to 4) {
  msgQueue.writeQueue("a")
}


val listBuffer = new ListBuffer[String]
while (!msgQueue.queue.isEmpty) {
  listBuffer += msgQueue.readQueue
}

msgQueue.queue.size
listBuffer.toString()

