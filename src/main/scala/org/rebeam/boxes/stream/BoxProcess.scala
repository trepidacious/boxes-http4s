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

import org.http4s._
import org.http4s.dsl._
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.json._
import org.rebeam.boxes.persistence.buffers._
import PrimFormats._
import ProductFormats._
import CollectionFormats._
import NodeFormats._
import BasicFormats._
import TaggedUnionFormats._

import org.rebeam.boxes.http4s._
import BoxOutgoing._

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

trait Dispatcher[I, O] extends OutgoingDispatcher[O] {
  /**
   * Called when client sends data
   */
  def fromClient(msg: I): Unit
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
private class RevisionOutgoingDispatcher extends OutgoingDispatcher[Revision] {
  var pendingRev = none[Revision]
  override def observe(newR: Revision): Unit = pendingRev = Some(newR)
  override def toClient(): Option[Revision] = {
    val r = pendingRev
    pendingRev = None
    r
  }
  override val lock = Lock()
}

/**
  * This just performs outgoing dispatch of revisions, by holding on to the most 
  * recent one, overwriting any previous values. Revisions are converted into
  * BoxOutgoing instances as appropriate, starting from a given document, and
  * then BoxOutgoing is converted to Text using json encoding.
  * In addition, accepts Text from client and uses BoxIncoming to decode and run
  * using the same ids as outgping messages.
  */
private class BoxTextDispatcher[T: Format](val document: T) extends Dispatcher[String, Text] {

  override val lock = Lock()
  
  //Use persistent ids
  val ids = IdsWeak()
  var pendingRev = none[Revision]
  override def observe(newR: Revision): Unit = pendingRev = Some(newR)
  override def toClient(): Option[Text] = {
    val r = pendingRev
    pendingRev = None
    r.map(r => BoxOutgoing.update(r, document, ids))
  }

  /**
   * Called when client sends data
   */
  def fromClient(msg: String): Unit = lock {
    BoxIncoming(msg, ids).run(document, ids)
  }
}

/**
  * Easy access to a Process[Task, Revision] using an AsyncObserver
  */
object BoxProcess {
  
  def observeByProcess[T](obs: => DispatchObserver[T]): BoxScript[Process[Task, T]] = {
    val ao = obs
    for {
      _ <- observe(ao)
    } yield ao.process
  }
  
  def observeRevisionByProcess: BoxScript[Process[Task, Revision]] = 
    observeByProcess{new DispatchObserver(new RevisionOutgoingDispatcher())}
  
  def observeTextByProcess[D: Format](document: D): BoxScript[Process[Task, Text]] = 
    observeByProcess{new DispatchObserver(new BoxTextDispatcher(document))}

}

object BoxExchange {
  def apply[D: Format](document: D): BoxScript[Exchange[WebSocketFrame, WebSocketFrame]] = {
    val dispatcher = new BoxTextDispatcher(document)
    val observer = new DispatchObserver(dispatcher)
    
    //Treat received text as commits to data
    val sink: Sink[Task, WebSocketFrame] = Process.constant {
      case Text(t, _) => Task.delay( dispatcher.fromClient(t) )
    }
    
    for {
      _ <- observe(observer)
    } yield Exchange(observer.process, sink)
  }
}

