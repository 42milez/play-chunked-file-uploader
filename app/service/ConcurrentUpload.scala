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
  def checkExistenceFor(chunkInfo: Map[String, Seq[String]])(implicit ec: ExecutionContext): Future[Boolean] = {
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
  def concatenateFileChunk(chunkInfo: Map[String, Seq[String]], chunk: Array[Byte])(implicit ec: ExecutionContext): Future[String] = {
    val chunkNumber: Int = chunkInfo("resumableChunkNumber").head.toInt
    val chunkSize: Int = chunkInfo("resumableChunkSize").head.toInt
    val currentChunkSize: Int = chunkInfo("resumableCurrentChunkSize").head.toInt
    val fileName: String = chunkInfo("resumableFilename").head
    val identifier: String = chunkInfo("resumableIdentifier").head
    val totalSize: Int = chunkInfo("resumableTotalSize").head.toInt
    val actorName: String = getActorName(identifier)
    val c: Chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, chunk, fileName, identifier, totalSize)
    // Concatenate chunks
    (supervisor ? new Data(actorName, c)).mapTo[Result] map {
      case r: Result =>
        r.status
    }
  }
}

class ConcurrentUploadService extends ConcurrentUploadServiceComponent with UploadServiceComponent {
  val supervisor = system.actorOf(ConcurrentUploader.props, "Supervisor")
}
