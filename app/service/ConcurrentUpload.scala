package service

import akka.actor.ActorRef
import akka.pattern.ask
import play.api.libs.Crypto.sign
import play.api.libs.concurrent.Akka._
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

import actor.ChunkConcatenatorProtocol.Chunk
import actor.ConcurrentUploader
import actor.ConcurrentUploaderProtocol._

/** */
trait UploadComponent {
  val supervisor: ActorRef
  def getActorName(identifier: String): String = { sign(identifier) }
}

/** */
trait ConcurrentUploadComponent { this: UploadComponent =>
  implicit private val timeout: akka.util.Timeout = 1 second

  /**
   *
   * @param chunkInfo
   * @return
   */
  def checkExistenceFor(chunkInfo: Map[String, Seq[String]])(implicit ec: ExecutionContext): Future[Boolean] = {
    val chunkNumber = chunkInfo("resumableChunkNumber").head.toInt
    val identifier = chunkInfo("resumableIdentifier").head
    val actorName = getActorName(identifier)
    (supervisor ? new Test(actorName, chunkNumber)).mapTo[Boolean]
  }

  /**
   *
   * @param chunkInfo
   * @param chunk
   * @return
   */
  def concatenateFileChunk(chunkInfo: Map[String, Seq[String]], chunk: Array[Byte])(implicit ec: ExecutionContext): Future[String] = {
    val chunkNumber = chunkInfo("resumableChunkNumber").head.toInt
    val chunkSize = chunkInfo("resumableChunkSize").head.toInt
    val currentChunkSize = chunkInfo("resumableCurrentChunkSize").head.toInt
    val fileName = chunkInfo("resumableFilename").head
    val identifier = chunkInfo("resumableIdentifier").head
    val totalSize = chunkInfo("resumableTotalSize").head.toInt
    val actorName = getActorName(identifier)
    val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, chunk, fileName, identifier, totalSize)
    // Concatenate chunks
    (supervisor ? new Data(actorName, chunk)).mapTo[Result] map {
      case r: Result =>
        r.status
    }
  }
}

class ConcurrentUpload extends ConcurrentUploadComponent with UploadComponent {
  val supervisor = system.actorOf(ConcurrentUploader.props, "Supervisor")
}
