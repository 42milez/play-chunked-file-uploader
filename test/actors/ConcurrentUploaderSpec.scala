package actors

import akka.actor.ActorSystem
import org.specs2.mutable.Specification
import play.api.libs.Crypto.sign
import play.api.test.WithApplication
import scala.concurrent.duration.DurationInt

import ChunkConcatenatorProtocol.Chunk
import ConcurrentUploaderProtocol._
import helper.AkkaHelper.TestEnvironment
import helper.ResumableHelper.{dummyChunk, chunkFirst => c1}

class ConcurrentUploaderSpec extends Specification {
  implicit private val timeout: akka.util.Timeout = 1 second

  //////////////////////////////////////////////////////////////////////
  // Test for "receive" function
  //////////////////////////////////////////////////////////////////////

  "ConcurrentUploader#receive" should {
    "receives a Test protocol" >> {
      "and returns \"false\"" in new TestEnvironment(ActorSystem("TestSystem-01")) {
        new WithApplication {
          val actorRef = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName = sign(c1("resumableIdentifier").head)
          val chunkNumber = c1("resumableChunkNumber").head.toInt
          actorRef ! new Test(actorName, chunkNumber)
          expectMsgPF() {
            case result: Boolean => result must equalTo(false)
          }
        }
      }
      "and returns \"true\" when the chunk was already uploaded" in new TestEnvironment(ActorSystem("TestSystem-02")) {
        new WithApplication {

          //////////////////////////////
          // upload a chunk
          val actorRef = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName = sign(c1("resumableIdentifier").head)
          val chunkNumber = c1("resumableChunkNumber").head.toInt
          val chunkSize = c1("resumableChunkSize").head.toInt
          val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
          val filename = c1("resumableFilename").head
          val identifier = c1("resumableIdentifier").head
          val totalSize = c1("resumableTotalSize").head.toInt
          val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
          actorRef ! new Data(actorName, chunk)

          //////////////////////////////
          // check a chunk for existence
          actorRef ! new Test(actorName, chunkNumber)
          expectMsgPF() {
            case result: Boolean => result must equalTo(true)
          }
        }
      }
    }
    "receives a Data protocol" in new TestEnvironment(ActorSystem("TestSystem-03")) {
      new WithApplication {
        val actorRef = system.actorOf(ConcurrentUploader.props, "Supervisor")
        val actorName = sign(c1("resumableIdentifier").head)
        val chunkNumber = c1("resumableChunkNumber").head.toInt
        val chunkSize = c1("resumableChunkSize").head.toInt
        val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
        val filename = c1("resumableFilename").head
        val identifier = c1("resumableIdentifier").head
        val totalSize = c1("resumableTotalSize").head.toInt
        val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
        actorRef ! new Data(actorName, chunk)
        expectMsgPF() {
          case result: ConcurrentUploaderProtocol.Result => result.status must equalTo("done")
        }
      }
    }
    "receives a Progress protocol" >> {
      "and return a Result protocol" in new TestEnvironment(ActorSystem("TestSystem-04")) {
        new WithApplication {
          val actorRef = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName = sign(c1("resumableIdentifier").head)
          val chunkNumber = c1("resumableChunkNumber").head.toInt
          val status = "done"
          actorRef ! new Progress(actorName, status, chunkNumber, self)
          expectMsgPF() {
            case result: ConcurrentUploaderProtocol.Result => result.status must equalTo("done")
          }
        }
      }
      "and returns a Result protocol when the progress is complete" in new TestEnvironment(ActorSystem("TestSystem-04")) {
        new WithApplication {

          //////////////////////////////
          // upload a chunk
          val actorRef = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName = sign(c1("resumableIdentifier").head)
          val chunkNumber = c1("resumableChunkNumber").head.toInt
          val chunkSize = c1("resumableChunkSize").head.toInt
          val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
          val filename = c1("resumableFilename").head
          val identifier = c1("resumableIdentifier").head
          val totalSize = c1("resumableTotalSize").head.toInt
          val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
          actorRef ! new Data(actorName, chunk)

          //////////////////////////////
          // complete upload
          val status = "complete"
          actorRef ! new Progress(actorName, status, chunkNumber, self)
          expectMsgPF() {
            case result: ConcurrentUploaderProtocol.Result => result.status must equalTo("complete")
          }
        }
      }
    }
  }
}
