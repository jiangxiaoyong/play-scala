import scala.collection.mutable.{ListBuffer, Queue}
val queue = new Queue[String]
queue.enqueue("a")
queue.enqueue("b")

for( i <- 1 to 10) {
  if(!queue.isEmpty) println(queue.dequeue())
}

var listBuffer = new ListBuffer[String]
listBuffer += "a"
listBuffer += "b"

listBuffer.toString()
