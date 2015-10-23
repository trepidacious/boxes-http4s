package org.rebeam.boxes

import scalaz.concurrent.Strategy
import scalaz.stream.async._
import scalaz.stream.async.mutable.Queue

package object http4s {
  //FIXME this should be a circularBuffer - implement this ASAP when it is in scalaz-stream release used by http4s
  def boxQueue[A](implicit S: Strategy): Queue[A] = unboundedQueue[A](S)
}
