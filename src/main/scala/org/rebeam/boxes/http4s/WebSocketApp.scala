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
import BoxUtils._
import BoxTypes._
import BoxScriptImports._

object WebSocketApp extends App {

  val data = atomic { create("a") }

  val route = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case req@ GET -> Root / "ws" =>
      val src = awakeEvery(1.seconds)(Strategy.DefaultStrategy, DefaultScheduler).map{ d => Text(s"Ping! $d") }
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay( println(t))
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
      //View the contents of data, and on changes enqueue the new value to dataQ immediately
      val dataQ = boxQueue[WebSocketFrame]

      val enqueueObserver = Observer(r => dataQ.enqueueOne(Text(data.get(r))).run)
      atomic { observe(enqueueObserver) }

      val src = dataQ.dequeue

      //Treat received text as commits to data
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay( atomic { data() = t } )
      }

      WS(Exchange(src, sink))
  }

  BlazeBuilder.bindHttp(8080)
    .withWebSockets(true)
    .mountService(route, "/http4s")
    .run
    .awaitShutdown()

}
