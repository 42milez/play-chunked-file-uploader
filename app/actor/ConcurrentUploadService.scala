package actor

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import play.api.libs.concurrent.Akka.system
import play.api.libs.Crypto.sign
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

// ======================================================================
//  New
// ======================================================================

/** */
trait UploadServiceComponent {
  val supervisor: ActorRef
  def getActorName(identifier: String): String = { sign(identifier) }
}

/** */
trait ConcurrentUploadServiceComponent { this: UploadServiceComponent =>
  implicit private val timeout: akka.util.Timeout = 1 second

  /**
   *
   * @param chunkInfo
   * @return
   */
  def checkExistenceFor(chunkInfo: Map[String, Seq[String]]): Future[Boolean] = {
    val chunkNumber: Int = chunkInfo("resumableChunkNumber").head.toInt
    val identifier: String = chunkInfo("resumableIdentifier").head
    val actorName: String = getActorName(identifier)
    (supervisor ? new Test(actorName, chunkNumber)).mapTo[Boolean]
  }

  /**
   *
   * @param chunkInfo
   * @param chunk
   * @return
   */
  def concatenateFileChunk(chunkInfo: Map[String, Seq[String]], chunk: Array[Byte]): Future[String] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val chunkNumber: Int = chunkInfo("resumableChunkNumber").head.toInt
    val chunkSize: Int = chunkInfo("resumableChunkSize").head.toInt
    val currentChunkSize: Int = chunkInfo("resumableCurrentChunkSize").head.toInt
    val filename: String = chunkInfo("resumableFilename").head
    val identifier: String = chunkInfo("resumableIdentifier").head
    val totalSize: Int = chunkInfo("resumableTotalSize").head.toInt
    val actorName: String = getActorName(identifier)
    val fc: FileChunk = FileChunk(chunkNumber, chunkSize, currentChunkSize, chunk, filename, identifier, totalSize)
    // Concatenate chunks
    (supervisor ? new Data(actorName, fc)).mapTo[Result] map {
      case r: Result =>
        r.status
    }
  }

  /** */
  class Uploader extends Actor {
    implicit private val timeout: akka.util.Timeout = 1 second
    private val children: scala.collection.mutable.Map[String, ActorRef] = scala.collection.mutable.Map.empty[String, ActorRef]

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
            fiRef ! (t.chunkNumber, sender())
          // NOT uploaded
          case None =>
            sender() ! false
        }
      // upload a chunk
      case d: Data =>
        concatenate(d.actorName, d.fc)
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
      * @param fc
      */
    def concatenate(actorName: String, fc: FileChunk): Unit = {
      children.get(actorName) match {
        // the actor is exist
        case Some(fiRef: ActorRef) =>
          fiRef ! (fc, sender())
        // the actor is NOT exist
        case None =>
          val fiRef = system.actorOf(Concatenator.props(fc.filename, fc.totalSize, fc.chunkSize), actorName)
          children.put(actorName, fiRef)
          fiRef ! (fc, sender())
      }
    }
  }

  object Uploader {
    def props = Props(new Uploader)
  }
}

class ConcurrentUploadService extends ConcurrentUploadServiceComponent with UploadServiceComponent {
  val supervisor = system.actorOf(Uploader.props, "Supervisor")
}

case class Test(actorName: String, chunkNumber: Int)
case class Data(actorName: String, fc: FileChunk)
case class Progress(actorName: String, status: String, chunkNumber: Int, senderRef: ActorRef)
case class Result(actorName: String, status: String, chunkNumber: Int)
