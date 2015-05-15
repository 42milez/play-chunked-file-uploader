package actor

import akka.actor._
import akka.pattern.ask
import play.api.libs.Crypto.sign
import play.api.libs.concurrent.Akka._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object ConcurrentUpload {

  import play.api.Play.current

  implicit private val timeout: akka.util.Timeout = 1 second
  private val supervisor: ActorRef = system.actorOf(Props[ConcurrentUpload], "Supervisor")

  def getActorName(identifier: String): String = { sign(identifier) }

  def checkExistenceFor(chunkInfo: Map[String, Seq[String]]): Future[Boolean] = {
    val chunkNumber: Int = chunkInfo("resumableChunkNumber").head.toInt
    val identifier: String = chunkInfo("resumableIdentifier").head
    val actorName: String = getActorName(identifier)
    (supervisor ? new Test(actorName, chunkNumber)).mapTo[Boolean]
  }

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
    (supervisor ? new UploadData(actorName, fc)).mapTo[UploadResult] map {
      case r: UploadResult =>
        r.status
    }
  }
}

class ConcurrentUpload extends Actor {

  import play.api.Play.current

  implicit private val timeout: akka.util.Timeout = 1 second

  private val children: scala.collection.mutable.Map[String, ActorRef] = scala.collection.mutable.Map.empty[String, ActorRef]

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
    case d: UploadData =>
      concatenate(d.actorName, d.fc)

    // in progress
    case p: UploadProgress =>
      p.senderRef ! new UploadResult(p.actorName, p.status, p.chunkNumber)

    // all chunks was uploaded
    case p: UploadProgress if p.status == "complete" =>
      children.get(p.actorName) match {
        case Some(fiRef: ActorRef) =>
          context.stop(fiRef)
          p.senderRef ! new UploadResult(p.actorName, p.status, p.chunkNumber)
      }
  }

  private def concatenate(actorName: String, fc: FileChunk): Unit = {
    children.get(actorName) match {
      // the actor is exist
      case Some(fiRef: ActorRef) =>
        fiRef ! (fc, sender())
      // the actor is NOT exist
      case None =>
        val fiRef = system.actorOf(FileInfo.props(fc.filename, fc.totalSize, fc.chunkSize), actorName)
        children.put(actorName, fiRef)
        fiRef ! (fc, sender())
    }
  }
}

case class Test(actorName: String, chunkNumber: Int)
case class UploadData(actorName: String, fc: FileChunk)
case class UploadProgress(actorName: String, status: String, chunkNumber: Int, senderRef: ActorRef)
case class UploadResult(actorName: String, status: String, chunkNumber: Int)
