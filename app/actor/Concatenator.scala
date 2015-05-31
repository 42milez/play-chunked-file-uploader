package actor

import akka.actor.{Actor, ActorRef, Props}
import java.io.{File, RandomAccessFile}
import java.nio.channels.ClosedChannelException
import play.api.Play
import play.api.Play.current
import scala.collection.mutable.{Set => MutableSet}
import scala.math.ceil

import ChunkConcatenatorProtocol.Chunk
import ConcurrentUploaderProtocol.Progress
import dao.FilesDAO
import models.{File => FileM}

class ChunkConcatenator(fileName: String, totalSize: Int, chunkSize: Int) extends Actor {
  protected val baseDir: String = Play.application.path + "/storage"
  protected val count: Int = ceil(totalSize.toDouble / chunkSize.toDouble).toInt
  protected val filePath: String = new File(baseDir, fileName).getAbsolutePath
  protected val uploadedChunks: MutableSet[Int] = MutableSet.empty[Int]

  def receive = {

    //////////////////////////////////////////////////////////////////////
    // RECEIVE A FILE CHUNK
    //////////////////////////////////////////////////////////////////////
    case (fc: Chunk, senderRef: ActorRef) =>
      val raf: RandomAccessFile = new RandomAccessFile(filePath, "rw")
      var isError: Boolean = false
      try {
        raf.seek((fc.chunkNumber - 1) * fc.chunkSize)
        raf.write(fc.data, 0, fc.currentChunkSize)
        uploadedChunks += fc.chunkNumber
      }
      catch {
        case _: ClosedChannelException =>
          isError = true
        case _: IndexOutOfBoundsException =>
          isError = true
      }
      finally {
        raf.close()
      }

      if (isError) {
        sender() ! new Progress(self.path.name, "error", fc.chunkNumber, senderRef)
      }
      else {
        if (uploadedChunks.size >= count) {
          val filesDao = new FilesDAO
          filesDao.insert(FileM(None, fc.filename))
          sender() ! new Progress(self.path.name, "complete", fc.chunkNumber, senderRef)
        }
        else {
          sender() ! new Progress(self.path.name, "done", fc.chunkNumber, senderRef)
        }
      }

    //////////////////////////////////////////////////////////////////////
    // RECEIVE A CHUNK NUMBER
    //////////////////////////////////////////////////////////////////////
    case (chunkNumber: Int, senderRef: ActorRef) =>
      // check existence for a chunk index
      val isUploadedChunk = uploadedChunks.contains(chunkNumber)
      senderRef ! isUploadedChunk
  }
}

object ChunkConcatenatorProtocol {
  case class Chunk(chunkNumber: Int, chunkSize: Int, currentChunkSize: Int,
                   data: Array[Byte], filename: String, identifier: String, totalSize: Int)
}

// See below for a practical design of creating an actor.
// Props in: http://doc.akka.io/docs/akka/snapshot/scala/actors.html
object ChunkConcatenator {
  def props(fileName: String, totalSize: Int, chunkSize: Int): Props =
    Props(new ChunkConcatenator(fileName, totalSize, chunkSize))
}
