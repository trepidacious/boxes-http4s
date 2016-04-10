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


object Data {

  //This represents an incoming commit in json, however we can't directly decode
  //it since we don't know T - this is just here for reference
  private case class BoxCommitJson[T](revisionIndex: Long, boxId: Long, boxContents: T)

  //This is what we can actually get from an incoming Text, by using a custom parser
  //to pull it apart, first into tokens for the whole incoming json, and then stripping
  //the outer object then revisionIndex and boxId field tokens away, leaving just tokens 
  //for the token-encoded box contents. We can then use a Replaces[T] to actually read
  //the tokens when the correct box is found, using the magic of typeclasses to know
  //at compile-time that we will use the right type T at run-time.
  case class BoxCommit(revisionIndex: Long, boxId: Long, boxTokens: TokenReader)

  //The much simpler outgoing message - we know the type T, so we can just encode with
  //JsonIO
  case class BoxUpdate[T](revisionIndex: Long, document: T)

  implicit def boxUpdateFormat[T: Format]: Format[BoxUpdate[T]] = productFormat2[Long, T, BoxUpdate[T]](BoxUpdate.apply)("revisionIndex", "document")
  
  def update[T: Format](r: Revision, t: T): Text = {
    val update = BoxUpdate(r.index, t)
    val json = JsonIO.toJsonString(r, update)
    Text(json)
  }
  
  private def isOpenDict(t: Token): Boolean = t match {
    case OpenDict(_, _) => true
    case _ => false
  }
  
  private def dictEntryNameOption(t: Token): Option[String] = t match {
    case DictEntry(key, _) => Some(key)
    case _ => None
  }
  
  def applyCommit[T: Format](s: String, document: T): Unit = {
    val c = commit(s)    
    Shelf.runReader(implicitly[Format[T]].replace(document, c.boxId), c.boxTokens)
  }
  
  //This code is awful. I'm sure this can be done recursively, but tokens are 
  //already mutable, so just do this imperatively for now. Note that this is
  //at least likely to be reasonably fast, since we just parse the json to
  //an ArrayBuffer of tokens once, then mess about with that buffer.
  //TODO make less awful
  @throws [IncorrectTokenException]
  def commit(s: String): BoxCommit = {
    val tokens = JsonIO.arrayBufferFromJsonString(s)
    
    if (tokens.size < 3) throw new IncorrectTokenException("< 2 tokens at start")  
    if (!isOpenDict(tokens.remove(0))) throw new IncorrectTokenException("first token not OpenDict")  
    if (tokens.remove(tokens.size - 1) != EndToken) throw new IncorrectTokenException("last token not EndToken")  
    if (tokens.remove(tokens.size - 1) != CloseDict) throw new IncorrectTokenException("penultimate token not CloseDict")  

    //We will fill these in as we find them
    var boxId = none[Long]
    var revisionIndex = none[Long]
    
    //We should now just have the dict entries for revisionIndex, boxId and 
    //boxContents. We can keep getting the first dict entry as long as it is
    //not the contents - when we get to them we need to stop
    var boxContentsFound = false
    while (!boxContentsFound) {
      if (tokens.size < 2) throw new IncorrectTokenException("< 2 tokens when requiring more fields")
      var name = dictEntryNameOption(tokens.remove(0))
      if (name.isEmpty) throw new IncorrectTokenException("token not dict entry as expected")
      if (name == Some("boxId")) {
        boxId = some(JsonCasting.toLong(tokens.remove(0)))
      } else if (name == Some("revisionIndex")) {
        revisionIndex = some(JsonCasting.toLong(tokens.remove(0)))
      } else if (name == Some("boxContents")) {
        boxContentsFound = true;
      } else {
        throw new IncorrectTokenException("dict entry for unwanted key " + name)
      }
    }
    
    //While we have not yet found boxId and revisionIndex, work back from the
    //end of the tokens
    while (boxId.isEmpty || revisionIndex.isEmpty) {
      if (tokens.size < 2) throw new IncorrectTokenException("< 2 tokens when requiring more fields")

      //The last token must be a long value for one or the other of the fields
      val l = JsonCasting.toInt(tokens.remove(tokens.size - 1))
      
      //Now the last token must be the dict entry - find out which field we got!
      var name = dictEntryNameOption(tokens.remove(tokens.size - 1))
      if (name.isEmpty) throw new IncorrectTokenException("token not dict entry as expected")
      if (name == Some("boxId")) {
        boxId = Some(l)
      } else if (name == Some("revisionIndex")) {
        revisionIndex = Some(l)
      } else {
        throw new IncorrectTokenException("dict entry for unwanted key " + name)
      }
    }
    
    //We should now have everything, and some tokens left for the reader...
    if (tokens.size > 0) {
      //Note we wrap the BufferTokenReader in a JsonTokenReader to apply
      //appropriate casting to the tokens when using e.g. pullLong, pullFloat etc.
      val commitOption = (revisionIndex |@| boxId){ BoxCommit(_, _, JsonTokenReader(BufferTokenReader(tokens.toList))) }
      commitOption.getOrElse(throw new IncorrectTokenException("invalid contents"))
    } else {
      throw new IncorrectTokenException("no tokens left for box contents")        
    }
    
  }
  
}

