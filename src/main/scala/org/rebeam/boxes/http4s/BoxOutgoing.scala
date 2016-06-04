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
import TaggedUnionFormats._

import org.rebeam.boxes.persistence.json._

import scala.collection.mutable.ArrayBuffer


sealed trait BoxOutgoing[T]

object BoxOutgoing {

  case class BoxUpdate[T](revisionIndex: Long, document: T) extends BoxOutgoing[T]

  implicit def boxOutgoingFormat[T: Format]: Format[BoxOutgoing[T]] = {
    implicit def boxUpdateFormat[U: Format]: Format[BoxUpdate[U]] = 
      productFormat2[Long, U, BoxUpdate[U]](BoxUpdate.apply)("revisionIndex", "document")

      taggedUnionFormat[BoxOutgoing[T]](
      {
        case "update" => boxUpdateFormat[T]
      },
      {
        case u: BoxUpdate[T] => Tagged("update", u)
      }
    )
  }
  
  def update[T: Format](r: Revision, t: T, ids: Ids): Text = {
    val update = BoxUpdate(r.index, t)
    val json = JsonIO.toJsonStringFromRevision(r, update, ids)
    Text(json)
  }

}

