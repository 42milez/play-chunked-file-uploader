package models

import akka.actor.Actor
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration.DurationInt

object Storage {
  case class Add(actorName: String, chunkNumber: Int)
  case class Remove(actorName: String)
  case class Test(actorName: String, chunkNumber: Int)
}

class Storage extends Actor {

  import Storage.{Add, Remove, Test}

  implicit private val timeout: akka.util.Timeout = 1 second

  private val uploadedChunks: MutableMap[String, Seq[Int]] = MutableMap.empty[String, Seq[Int]]

  def receive = {
    case a: Add =>
      uploadedChunks.get(a.actorName) match {
        case Some(chunkNumbers: Seq[Int]) =>
          uploadedChunks.put(a.actorName, chunkNumbers :+ a.chunkNumber)
        case None =>
          uploadedChunks.put(a.actorName, Seq(a.chunkNumber))
      }
    case t: Test =>
      // check existence for a chunk index
      uploadedChunks.get(t.actorName) match {
        case Some(chunkNumbers: Seq[Int]) =>
          sender() ! chunkNumbers.contains(t.chunkNumber)
        case None =>
          sender() ! false
      }
    case r: Remove =>
      // remove a chunk index group
      uploadedChunks.remove(r.actorName)
  }
}
