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

object BoxProcess {
  def observeByProcess: BoxScript[Process[Task, Revision]] = {
    val ao = new AsyncObserver()
    for {
      _ <- observe(ao)
    } yield ao.process
  }
}

private class AsyncObserver extends Observer {
  
  val lock = Lock()
  var pendingPull = none[Revision => Unit]
  var pendingRev = none[Revision]
  
  def observe(newR: Revision): Unit = lock {
    //If we have a pull function pending, deliver to it immediately, otherwise
    //store the new revision for later
    pendingPull match {
      case Some(pull) => 
        //TODO we could coalesce the newR with any pendingRev first, here
        //we just ignore it
        //Now clear pendingRev since we will deliver it, and pendingPull since
        //it is about to be delivered to
        pendingRev = None
        pendingPull = None
        //Important that get the correct state BEFORE calling pull, since it
        //may attempt to immediately reregister
        pull.apply(newR)
        
      case None =>
        //TODO we could coalesce the newR with any current pendingRev first, here
        //we just overwrite it
        pendingRev = Some(newR)
    }
  }
  
  def pullEither(pullE: (Throwable \/ Revision) => Unit): Unit = pull((r: Revision) => pullE(\/-(r)))
  
  def pull(pullF: Revision => Unit): Unit = lock {
    //Only one pull registered at a time
    if (pendingPull.isDefined) throw new RuntimeException (
      "AsyncObserver pull called when pull was already pending - " +
      "call pull again only when previous pull function is called back"
    )
    
    //Deliver data straight away if we have it, otherwise store the pull as pending
    pendingRev match {
      case Some(r) => 
        //Clear pendingRev, since we are about to deliver it
        pendingRev = None
        //Important that get the correct state BEFORE calling pull, since it
        //may attempt to immediately reregister
        pullF.apply(r)
        
      case None => 
        pendingPull = Some(pullF)
    }
  }
  
  val process: Process[Task, Revision] = Process.repeatEval(Task.async { (cb: Throwable \/ Revision => Unit) => pullEither(cb)})
}
