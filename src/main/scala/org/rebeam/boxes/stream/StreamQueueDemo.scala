package org.rebeam.boxes.stream

import org.rebeam.boxes.core._

import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import scalaz.concurrent.Task
import scalaz.stream.async._
import scalaz.stream._

object StreamQueueDemo extends App {

  //When available, use circularBuffer - it's what we actually want...
  val q = unboundedQueue[String]

  val s = atomic { create("a") }

  //View the contents of s, and on changes enqueue the new value to q immediately
  val enqueueObserver = Observer(r => q.enqueueOne(s.get(r)))
  atomic { observe(enqueueObserver) }

  def puts(ln: String): Task[Unit] = Task { println(">>" + ln) }
  val stdout = Process constant (puts _) toSource

  val printChanges = q.dequeue to stdout

  //Slightly hackily, start a thread to make some regular changes we can observe
  val makeChanges = new Thread(new Runnable(){
    def run(): Unit = {
      for(i <- Range(1, 100)) {
        atomic { s() = i.toString }
        println("s() = " + atomic{s()} )
        Thread.sleep(100)
      }
    }
  })
  makeChanges.setDaemon(true)
  makeChanges.start()

  //Start printing the changes
  printChanges.run.run

}
