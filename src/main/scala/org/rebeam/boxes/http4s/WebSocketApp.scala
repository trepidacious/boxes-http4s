package org.rebeam.boxes.http4s

import org.http4s._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.websocket.WebsocketBits._
import org.http4s.dsl._
import org.http4s.server.websocket._

import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz.concurrent.Strategy
import scalaz.stream.async.unboundedQueue
import scalaz.stream.{Process, Sink}
import scalaz.stream.{DefaultScheduler, Exchange}
import scalaz.stream.time.awakeEvery


import org.rebeam.boxes.core._
import org.rebeam.boxes.stream.BoxProcess._
import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.formats._
import PrimFormats._
import ProductFormats._
import CollectionFormats._
import NodeFormats._
import BasicFormats._

import org.rebeam.boxes.persistence.json._

import scalaz._
import Scalaz._

object WebSocketApp extends App {

  case class Person(name: Box[String], age: Box[Int]) {
    def asString: BoxScript[String] = (name() |@| age()){"Person(" + _ + ", " + _ + ")"}
  }

  object Person {
    def default: BoxScript[Person] = default("", 0)
    def default(name: String, age: Int): BoxScript[Person] = (create(name) |@| create(age)){Person(_, _)}
  }

  implicit val personFormat = nodeFormat2(Person.apply, Person.default)("name", "age")

  val p = atomic { Person.default("bob", 42) }

  println("name id " + p.name.id + ", age id " + p.age.id)

  val data = atomic { create("a") }

  val route = HttpService {
    
    case req@ GET -> Root / "person" =>    
      // val revisions = atomic { observeRevisionByProcess }
      // 
      // val src = revisions.map(r => BoxOutgoing.update(r, p))

      val src = atomic { observeTextByProcess(p) }

      //Treat received text as commits to data
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        //FIXME need to get ids from dispatcher
        case Text(t, _) => Task.delay( BoxIncoming(t).run(p, IdsDefault()) )
      }

      WS(Exchange(src, sink))
    
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case req@ GET -> Root / "ws" =>
      val src = awakeEvery(1.seconds)(Strategy.DefaultStrategy, DefaultScheduler).map{ d => Text(s"Ping! $d") }
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay(println(t))
        case f          => Task.delay(println(s"Unknown type: $f"))
      }
      WS(Exchange(src, sink))

    case req@ GET -> Root / "wsecho" =>
      val q = unboundedQueue[WebSocketFrame]
      val src = q.dequeue.collect {
        case Text(msg, _) => Text("Echoing: " + msg)
      }
      WS(Exchange(src, q.enqueue))

    case req@ GET -> Root / "boxes" =>    
      val revisions = atomic { observeRevisionByProcess }
      
      val src = revisions.map(r => Text(data.get(r)))

      //Treat received text as commits to data
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay( atomic { data() = t } )
      }

      WS(Exchange(src, sink))
      
  }

  BlazeBuilder.bindHttp(8080)
    .withWebSockets(true)
    .mountService(route, "/api")
    .run
    .awaitShutdown()

}
