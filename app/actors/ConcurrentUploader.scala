package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.concurrent.Akka.system
import play.api.Play.current
import scala.concurrent.duration.DurationInt

import ChunkConcatenatorProtocol.Chunk
import ConcurrentUploaderProtocol._

/** */
class ConcurrentUploader extends Actor {
  implicit private val timeout: akka.util.Timeout = 1 second
  private val children = scala.collection.mutable.Map.empty[String, ActorRef]

  /**
   *
   * @return
   */
  def receive = {
    // Check whether a chunk was already uploaded.
    case t: Test =>
      children.get(t.actorName) match {
        // uploaded
        case Some(fiRef: ActorRef) =>
          fiRef ! (new ChunkConcatenatorProtocol.Test(t.chunkNumber), sender())
        // NOT uploaded
        case None =>
          sender() ! false
      }
    // upload a chunk
    case d: Data =>
      concatenate(d.actorName, d.chunk)
    // all chunks was uploaded
    case p: Progress if p.status == "complete" =>
      children.get(p.actorName) match {
        case Some(fiRef: ActorRef) =>
          context.stop(fiRef)
          children.remove(p.actorName)
          p.senderRef ! new Result(p.actorName, p.status, p.chunkNumber)
      }
    // in progress
    case p: Progress =>
      p.senderRef ! new Result(p.actorName, p.status, p.chunkNumber)
  }

  /**
   *
   * @param actorName
   * @param c
   */
  def concatenate(actorName: String, c: Chunk): Unit = {
    children.get(actorName) match {
      // the actor is exist
      case Some(fiRef: ActorRef) =>
        fiRef ! (c, sender())
      // the actor is NOT exist
      case None =>
        val fiRef = system.actorOf(ChunkConcatenator.props(c.fileName, c.totalSize, c.chunkSize), actorName)
        children.put(actorName, fiRef)
        fiRef ! (c, sender())
    }
  }
}

object ConcurrentUploaderProtocol {
  case class Test(actorName: String, chunkNumber: Int)
  case class Data(actorName: String, chunk: Chunk)
  case class Progress(actorName: String, status: String, chunkNumber: Int, senderRef: ActorRef)
  case class Result(actorName: String, status: String, chunkNumber: Int)
}

object ConcurrentUploader {
  def props = Props(new ConcurrentUploader)
}
