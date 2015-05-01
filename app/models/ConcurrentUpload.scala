package models

import play.api.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import akka.actor._
import akka.ConfigurationException
import akka.pattern.ask
import play.api.libs.concurrent.Akka._
import play.api.libs.Crypto.sign

object ConcurrentUpload {

  import play.api.Play.current
  import models.Storage.{Add, Remove, Test}

  implicit private val timeout: akka.util.Timeout = 1 second

  private val supervisor: ActorRef = system.actorOf(Props[ConcurrentUpload], "Supervisor")
  private val storage: ActorRef = system.actorOf(Props[Storage], "Storage")

  def getActorName(identifier: String): String = {
    sign(identifier)
  }

  def checkExistenceFor(chunkInfo: Map[String, Seq[String]]): Future[Boolean] = {
    val chunkNumber: Int = chunkInfo("resumableChunkNumber").head.toInt
    val identifier: String = chunkInfo("resumableIdentifier").head
    val actorName: String = getActorName(identifier)
    (storage ? new Test(actorName, chunkNumber)).mapTo[Boolean]
  }

  def concatenateFileChunk(chunkInfo: Map[String, Seq[String]], chunk: Array[Byte]): Future[String] = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val chunkNumber: Int = chunkInfo("resumableChunkNumber").head.toInt
    val chunkSize: Int = chunkInfo("resumableChunkSize").head.toInt
    val currentChunkSize: Int = chunkInfo("resumableCurrentChunkSize").head.toInt
    val filename: String = chunkInfo("resumableFilename").head
    val identifier: String = chunkInfo("resumableIdentifier").head
    val relativePath: String = chunkInfo("resumableRelativePath").head
    val totalSize: Int = chunkInfo("resumableTotalSize").head.toInt
    val actorName: String = getActorName(identifier)
    val fc: FileChunk = FileChunk(chunkNumber, chunkSize, currentChunkSize, chunk, filename, identifier, relativePath, totalSize)

    // Concatenate chunks
    (supervisor ? new UploadData(actorName, fc)).mapTo[UploadResult] map {
      case r: UploadResult if r.status == "done" =>
        Logger.info("UPLOADING A CHUNK HAS DONE!")
        storage ! new Add(r.actorName, r.chunkNumber)
        r.status
      case r: UploadResult if r.status == "complete" =>
        Logger.info("ALL CHUNKS HAS BEEN UPLOADED!")
        storage ! new Remove(r.actorName)
        r.status
    }
  }

  case class UploadData(actorName: String, fc: FileChunk)
  case class UploadProgress(actorName: String, msg: String, chunkNumber: Int, senderRef: ActorRef)
  case class UploadResult(actorName: String, status: String, chunkNumber: Int)
}

class ConcurrentUpload extends Actor {

  import play.api.Play.current
  import models.ConcurrentUpload.{UploadData, UploadProgress, UploadResult}

  implicit private val timeout: akka.util.Timeout = 1 second

  private val children: scala.collection.mutable.Map[String, ActorRef] = scala.collection.mutable.Map.empty[String, ActorRef]

  def receive = {
    case d: UploadData =>
      concatenate(d.actorName, d.fc)
    case p: UploadProgress if p.msg == "done" =>
      p.senderRef ! new UploadResult(p.actorName, p.msg, p.chunkNumber)
    case p: UploadProgress if p.msg == "complete" =>
      children.get(p.actorName) match {
        case Some(childRef: ActorRef) =>
          context.stop(childRef)
          p.senderRef ! new UploadResult(p.actorName, p.msg, p.chunkNumber)
      }
  }

  private def concatenate(actorName: String, fc: FileChunk): Unit = {
    children.get(actorName) match {
      case Some(fiRef: ActorRef) =>
        fiRef ! (fc, sender())
      case None =>
        val fiRef = system.actorOf(FileInfo.props(fc.filename, fc.totalSize, fc.chunkSize), actorName)
        children.put(actorName, fiRef)
        fiRef ! (fc, sender())
    }
  }
}
