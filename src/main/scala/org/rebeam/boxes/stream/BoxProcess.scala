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

trait Dispatcher[I, O] extends OutgoingDispatcher[O] {
  /**
   * Called when client sends data
   */
  def fromClient(msg: I): Unit
}

trait OutgoingDispatcher[T] {
  /**
   * Called when there is a new revision available
   */
  def observe(newR: Revision): Unit
  
  /**
   * Called to check for new data to send to client 
   */
  def toClient(): Option[T]
  
  /**
   * Used to lock while handling data
   */
  def lock: Lock
}

private class DispatchObserver[T](d: OutgoingDispatcher[T]) extends Observer {
  
  var pendingPull = none[T => Unit]
  
  def observe(newR: Revision): Unit = d.lock {
    d.observe(newR)
    
    //If we have a pending pull and dispatcher now has a
    //message for client, give the message to the pull
    for {
      pull <- pendingPull
      msg <- d.toClient
    } {
      //Clear this before calling pull, in case we get called back immediately
      pendingPull = None
      pull.apply(msg)
    }
  }
    
  def pull(pullF: T => Unit): Unit = d.lock {
    //Only one pull registered at a time
    if (pendingPull.isDefined) throw new RuntimeException (
      "DispatchObserver pull called when pull was already pending - " +
      "call pull again only when previous pull function is called back"
    )
    
    //Deliver data straight away if we have it, otherwise store the pull as pending
    d.toClient match {
      case Some(s) => pullF.apply(s)        
      case None => pendingPull = Some(pullF)
    }
  }
  
  def pullEither(pullE: (Throwable \/ T) => Unit): Unit = pull((t: T) => pullE(\/-(t)))
  val process: Process[Task, T] = Process.repeatEval(Task.async { (cb: Throwable \/ T => Unit) => pullEither(cb)})
}

/**
 * This just performs outgoing dispatch of revisions, by holding on to the most 
 * recent one, overwriting any previous values.
 */
private class BasicOutgoingDispatcher extends OutgoingDispatcher[Revision] {
  var pendingRev = none[Revision]
  override def observe(newR: Revision): Unit = pendingRev = Some(newR)
  override def toClient(): Option[Revision] = {
    val r = pendingRev
    pendingRev = None
    r
  }
  override val lock = Lock()
}

private class AsyncObserver extends DispatchObserver(new BasicOutgoingDispatcher)

