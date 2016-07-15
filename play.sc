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

for( i <- 1 to 3) {
  msgQueue.writeQueue("a")
}


val listBuffer = new ListBuffer[String]
for( i <- 1 to 3) {
  if (!msgQueue.queue.isEmpty) {
    listBuffer += msgQueue.readQueue
  }
}

listBuffer.toString()
