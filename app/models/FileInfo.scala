package models

import java.io.{File, RandomAccessFile}
import scala.math.ceil
import akka.actor.{Actor, ActorRef, Props}
import play.api.Play
import play.api.Play.current

// See below for a practical design of creating an actor.
// Props in: http://doc.akka.io/docs/akka/snapshot/scala/actors.html
object FileInfo {
  def props(fileName: String, totalSize: Int, chunkSize: Int): Props = Props(new FileInfo(fileName, totalSize, chunkSize))
}

class FileInfo(fileName: String, totalSize: Int, chunkSize: Int) extends Actor {

  import models.ConcurrentUpload.UploadProgress

  private var uploadedChunks: Int = 0
  private val baseDir: String = Play.application.path + "/storage/images"
  private val count: Int = ceil(totalSize.toDouble / chunkSize.toDouble).toInt
  private val filePath: String = new File(baseDir, fileName).getAbsolutePath

  def receive = {
    case (fc: FileChunk, senderRef: ActorRef) =>
      val raf: RandomAccessFile = new RandomAccessFile(filePath, "rw")
      raf.seek((fc.chunkNumber - 1) * fc.chunkSize)
      raf.write(fc.data, 0, fc.currentChunkSize)
      raf.close()
      uploadedChunks += 1
      if (uploadedChunks >= count) {
        sender() ! new UploadProgress(self.path.name, "complete", fc.chunkNumber, senderRef)
      }
      else {
        sender() ! new UploadProgress(self.path.name, "done", fc.chunkNumber, senderRef)
      }
  }
}

case class FileChunk(chunkNumber: Int, chunkSize: Int, currentChunkSize: Int,
                     data: Array[Byte], filename: String, identifier: String, relativePath: String, totalSize: Int)
