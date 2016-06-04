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

//This is what we can actually get from an incoming Text, by using a custom parser
//to pull it apart, first into tokens for the whole incoming json, and then stripping
//the outer object then revisionIndex and boxId field tokens away, leaving just tokens 
//for the token-encoded box contents. We can then use a Replaces[T] to actually read
//the tokens when the correct box is found, using the magic of typeclasses to know
//at compile-time that we will use the right type T at run-time.

//The "command" part of each incoming message, which determines what to
//do with the "data" part. Can be run when provided with a model to run on, 
//and the data tokens, and Ids to identify boxes/nodes etc..
sealed trait BoxIncomingCommand {
  def revisionIndex: Long
  def run[M: Format](model: M, ids: Ids, dataTokens: List[Token])
}

object BoxIncomingCommand {
  /**
    * Used to keep a connection alive, indicating the client is still connected.
    * Carries the revision index since this is useful in tracking where the client
    * is up to.
    */
  case class Ping(revisionIndex: Long) extends BoxIncomingCommand {
    def run[M: Format](model: M, ids: Ids, dataTokens: List[Token])= {}
  }
  /**
    * Trigger replacement of the contents of Box with given boxId.
    * The new contents are decoded from the data tokens.
    */
  case class Replace(revisionIndex: Long, boxId: Long) extends BoxIncomingCommand {
    def run[M: Format](model: M, ids: Ids, dataTokens: List[Token]): Unit = {
      Shelf.runRepeatedReader(
        implicitly[Format[M]].replace(model, boxId), 
        JsonTokenReader.maximalCasting(BufferTokenReader(dataTokens)),
        ids
      )    
    }
  }
  /**
    * Trigger modification of a data item containing the Box with given boxId.
    * The modification action is decoded from the data tokens.
    */
  case class Modify(revisionIndex: Long, boxId: Long) extends BoxIncomingCommand {
    def run[M: Format](model: M, ids: Ids, dataTokens: List[Token]): Unit = {
      Shelf.runRepeatedReader(
        implicitly[Format[M]].modify(model, boxId), 
        JsonTokenReader.maximalCasting(BufferTokenReader(dataTokens)),
        ids
      )    
    }    
  }
}

/**
  * The full contents of an incoming message, including the command, and the
  * tokens that make up the data that command will use.
  */
case class BoxIncoming(command: BoxIncomingCommand, dataTokens: List[Token]) {
  def run[M: Format](model: M, ids: Ids): Unit = command.run(model, ids, dataTokens)
}

object BoxIncoming {
  
  import BoxIncomingCommand._
  
  //Format for each BoxIncomingCommand type, then for BoxIncomingMessage itself
  implicit val boxIncomingCommandFormat = {
    implicit val pingFormat = productFormat1(Ping.apply)("revisionIndex")
    implicit val replaceFormat = productFormat2(Replace.apply)("revisionIndex", "boxId")
    implicit val modifyFormat = productFormat2(Modify.apply)("revisionIndex", "boxId")
    
    taggedUnionFormat[BoxIncomingCommand](
      {
        case "ping" => pingFormat
        case "replace" => replaceFormat
        case "modify" => modifyFormat
      },
      {
        case p: Ping => Tagged("ping", p)
        case r: Replace => Tagged("replace", r)
        case m: Modify => Tagged("modify", m)
      }
    )
  }

  //FIXME: This is a bit messy.
  @throws [IncorrectTokenException]
  def apply(s: String, ids: Ids = IdsDefault()): BoxIncoming = {
    val tokens = JsonIO.arrayBufferFromJsonString(s)
    
    //We expect at least open array, close array, end token
    if (tokens.size < 3) throw new IncorrectTokenException("< 3 tokens at start")  
    if (!isOpenArr(tokens.remove(0))) throw new IncorrectTokenException("first token not OpenArray")  
    if (tokens.remove(tokens.size - 1) != EndToken) throw new IncorrectTokenException("last token not EndToken")  
    if (tokens.remove(tokens.size - 1) != CloseArr) throw new IncorrectTokenException("penultimate token not CloseArray")  

    //The tokens now denote the command, then the data for the command.
    //First pull out the command
    val readerBuffer = BufferTokenReader(tokens.toList)
    val reader = JsonTokenReader.maximalCasting(readerBuffer)
    val command = Shelf.runRepeatedReader(boxIncomingCommandFormat.read, reader, ids)._2
    
    //We now just have the tokens for the data left in the reader buffer
    val dataTokens = readerBuffer.remainingTokens

    //Here's our incoming message 
    BoxIncoming(command, dataTokens)
  }
  
  private def isOpenArr(t: Token): Boolean = t match {
    case OpenArr(_) => true
    case _ => false
  }
  
}
