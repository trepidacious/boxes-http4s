package org.rebeam.boxes.http4s

import argonaut._, Argonaut._

import org.http4s._
import org.http4s.dsl._
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._

import scalaz._
import Scalaz._

import org.rebeam.boxes.core._
import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.json._
import org.rebeam.boxes.persistence.buffers._
import PrimFormats._
import ProductFormats._
import CollectionFormats._
import NodeFormats._
import BasicFormats._

import org.rebeam.boxes.persistence.json._

import scala.collection.mutable.ArrayBuffer


object BoxOutgoing {

  case class BoxUpdate[T](revisionIndex: Long, document: T)

  implicit def boxUpdateFormat[T: Format]: Format[BoxUpdate[T]] = productFormat2[Long, T, BoxUpdate[T]](BoxUpdate.apply)("revisionIndex", "document")
  
  def update[T: Format](r: Revision, t: T): Text = {
    val update = BoxUpdate(r.index, t)
    val json = JsonIO.toJsonString(r, update)
    Text(json)
  }
  
}

