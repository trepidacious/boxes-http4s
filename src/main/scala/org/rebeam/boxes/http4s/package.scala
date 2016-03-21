package org.rebeam.boxes

import scalaz.concurrent.Strategy
import scalaz.stream.async._
import scalaz.stream.async.mutable.Queue

package object http4s {
  //TODO what's the best size? 1 should be fine when only the most recent state matters.
  def boxQueue[A]: Queue[A] = circularBuffer[A](1)
}
