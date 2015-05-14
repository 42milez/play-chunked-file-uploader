package models

import java.io.{File, RandomAccessFile}
import java.nio.channels.ClosedChannelException
import scala.collection.mutable.{Set => MutableSet}
import scala.math.ceil
import akka.actor.{Actor, ActorRef, Props}
import play.api.{Logger, Play}
import play.api.Play.current

// See below for a practical design of creating an actor.
// Props in: http://doc.akka.io/docs/akka/snapshot/scala/actors.html
object FileInfo {
  def props(fileName: String, totalSize: Int, chunkSize: Int): Props = Props(new FileInfo(fileName, totalSize, chunkSize))
}

class FileInfo(fileName: String, totalSize: Int, chunkSize: Int) extends Actor {

  import models.ConcurrentUpload.UploadProgress
  
  private val baseDir: String = Play.application.path + "/storage"
  private val count: Int = ceil(totalSize.toDouble / chunkSize.toDouble).toInt
  private val filePath: String = new File(baseDir, fileName).getAbsolutePath
  private val uploadedChunks: MutableSet[Int] = MutableSet.empty[Int]

  def receive = {
    case (fc: FileChunk, senderRef: ActorRef) =>

      Logger.debug(fc.chunkNumber + ": chunk size         => " + fc.chunkSize)
      Logger.debug(fc.chunkNumber + ": current chunk size => " + fc.currentChunkSize)
      Logger.debug(fc.chunkNumber + ": file name          => " + fc.filename)
      Logger.debug(fc.chunkNumber + ": identifier         => " + fc.identifier)
      Logger.debug(fc.chunkNumber + ": total size         => " + fc.totalSize)

      val raf: RandomAccessFile = new RandomAccessFile(filePath, "rw")
      var isError: Boolean = false

      try {
        raf.seek((fc.chunkNumber - 1) * fc.chunkSize)
        raf.write(fc.data, 0, fc.currentChunkSize)
        uploadedChunks += fc.chunkNumber
      }
      catch {
        case _: ClosedChannelException =>
          Logger.debug(fc.chunkNumber + ": ClosedChannelException has occurred.")
          isError = true
        case _: IndexOutOfBoundsException =>
          Logger.debug(fc.chunkNumber + ": IndexOutOfBoundsException has occurred.")
          isError = true
      }
      finally {
        // Close the channel in finally block to avoid ClosedChannelException.
        raf.close()
      }

      if (isError) {
        sender() ! new UploadProgress(self.path.name, "error", fc.chunkNumber, senderRef)
      }
      else {
        Logger.debug(fc.chunkNumber + ": uploaded chunks    => " + uploadedChunks.size + "/" + count)
        if (uploadedChunks.size >= count) {
          Logger.info("ALL CHUNKS HAS BEEN UPLOADED!")
          sender() ! new UploadProgress(self.path.name, "complete", fc.chunkNumber, senderRef)
        }
        else {
          Logger.info("UPLOADING A CHUNK HAS DONE!")
          sender() ! new UploadProgress(self.path.name, "done", fc.chunkNumber, senderRef)
        }
      }
    case (chunkNumber: Int, senderRef: ActorRef) =>
      // check existence for a chunk index
      val isUploadedChunk = uploadedChunks.contains(chunkNumber)
      senderRef ! isUploadedChunk
    case _ =>
      Logger.error("Some Error has occurred.")
  }
}

case class FileChunk(chunkNumber: Int, chunkSize: Int, currentChunkSize: Int,
                     data: Array[Byte], filename: String, identifier: String, totalSize: Int)
