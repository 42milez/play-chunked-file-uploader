package actors

import akka.actor.{Actor, ActorRef, Props}
import java.io.{File, RandomAccessFile}
import java.nio.channels.ClosedChannelException
import play.api.Play
import play.api.Play.current
import scala.collection.mutable.{Set => MutableSet}
import scala.math.ceil

import ChunkConcatenatorProtocol.{Chunk, Test}
import ConcurrentUploaderProtocol.Progress
import dao.FilesDAO
import models.{File => FileM}

class ChunkConcatenator(fileName: String, totalSize: Int, chunkSize: Int) extends Actor {
  protected val baseDir = Play.application.path + "/storage"
  protected val count = ceil(totalSize.toDouble / chunkSize.toDouble).toInt
  protected val filePath = new File(baseDir, fileName).getAbsolutePath
  protected val uploadedChunks = MutableSet.empty[Int]

  def receive = {

    //////////////////////////////////////////////////////////////////////
    // RECEIVE A FILE CHUNK
    //////////////////////////////////////////////////////////////////////
    case (c: Chunk, senderRef: ActorRef) =>
      val raf: RandomAccessFile = new RandomAccessFile(filePath, "rw")
      var isError: Boolean = false
      try {
        raf.seek((c.chunkNumber - 1) * c.chunkSize)
        raf.write(c.data, 0, c.currentChunkSize)
        uploadedChunks += c.chunkNumber
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
        sender() ! new Progress(self.path.name, "error", c.chunkNumber, senderRef)
      }
      else {
        if (uploadedChunks.size >= count) {
          val filesDao = new FilesDAO
          filesDao.insert(FileM(None, c.fileName))
          sender() ! new Progress(self.path.name, "complete", c.chunkNumber, senderRef)
        }
        else {
          sender() ! new Progress(self.path.name, "done", c.chunkNumber, senderRef)
        }
      }

    //////////////////////////////////////////////////////////////////////
    // RECEIVE A CHUNK NUMBER
    //////////////////////////////////////////////////////////////////////
    case (t: Test, senderRef: ActorRef) =>
      // check existence for a chunk index
      val isUploadedChunk = uploadedChunks.contains(t.chunkNumber)
      senderRef ! isUploadedChunk
  }
}

object ChunkConcatenatorProtocol {
  case class Chunk(chunkNumber: Int, chunkSize: Int, currentChunkSize: Int,
                   data: Array[Byte], fileName: String, identifier: String, totalSize: Int)
  case class Test(chunkNumber: Int)
}

// See below for a practical design of creating an actor.
// Props in: http://doc.akka.io/docs/akka/snapshot/scala/actors.html
object ChunkConcatenator {
  def props(fileName: String, totalSize: Int, chunkSize: Int): Props =
    Props(new ChunkConcatenator(fileName, totalSize, chunkSize))
}
