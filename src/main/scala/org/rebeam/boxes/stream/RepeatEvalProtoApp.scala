package org.rebeam.boxes.stream

import org.rebeam.boxes.core._

import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.stream.async._

import scalaz._
import Scalaz._

import org.rebeam.boxes.core.util.Lock

object RepeatEvalProtoApp extends App {
  
  val s = atomic { create("a") }
  
  val o = new AsyncObserver()
  atomic { observe(o) }
  
  val p = o.process

  val printChanges = p.map(r => ">> " + s.get(r)).to(io.stdOutLines)

  //Slightly hackily, start a thread to make some regular changes we can observe
  val makeChanges = new Thread(new Runnable(){
    def run(): Unit = {
      for(i <- Range(1, 10)) {
        atomic { s() = i.toString }
        println("s() = " + atomic{s()} )
        Thread.sleep(10)
      }
    }
  })
  makeChanges.setDaemon(true)
  makeChanges.start()
  
  //Start printing the changes
  println("Starting first one...")
  Task.fork(printChanges.run).run
  
}
