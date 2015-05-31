package actor

import akka.actor.ActorSystem
import akka.util.Timeout
import org.specs2.mutable.Specification
import play.api.libs.Crypto.sign
import play.api.test.WithApplication
import scala.concurrent.duration.DurationInt

import ConcurrentUploaderProtocol._
import helper.AkkaHelper.TestEnvironment
import helper.ResumableHelper.{dummyChunk, dummyParams}

class ConcurrentUploadServiceSpec extends Specification {
  implicit private val timeout: Timeout = 1 second

  //////////////////////////////////////////////////////////////////////
  // Test for "receive" function
  //////////////////////////////////////////////////////////////////////

  "ConcurrentUploader#receive" should {
    "receives a Test protocol" >> {
      "and returns \"false\"" in new TestEnvironment(ActorSystem("TestSystem-01")) {
        new WithApplication {
          val actorRef    = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName   = sign(dummyParams("resumableIdentifier").head)
          val chunkNumber = dummyParams("resumableChunkNumber").head.toInt
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
          val actorRef         = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName        = sign(dummyParams("resumableIdentifier").head)
          val chunkNumber      = dummyParams("resumableChunkNumber").head.toInt
          val chunkSize        = dummyParams("resumableChunkSize").head.toInt
          val currentChunkSize = dummyParams("resumableCurrentChunkSize").head.toInt
          val filename         = dummyParams("resumableFilename").head
          val identifier       = dummyParams("resumableIdentifier").head
          val totalSize        = dummyParams("resumableTotalSize").head.toInt
          val fc               = FileChunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
          actorRef ! new Data(actorName, fc)

          //////////////////////////////
          // check a chunk for existence
          actorRef ! new Test(actorName, chunkNumber)
          expectMsgPF() {
            case r: Boolean => r must equalTo(true)
          }
        }
      }
    }
    "receives a Data protocol" in new TestEnvironment(ActorSystem("TestSystem-03")) {
      new WithApplication {
        val actorRef         = system.actorOf(ConcurrentUploader.props, "Supervisor")
        val actorName        = sign(dummyParams("resumableIdentifier").head)
        val chunkNumber      = dummyParams("resumableChunkNumber").head.toInt
        val chunkSize        = dummyParams("resumableChunkSize").head.toInt
        val currentChunkSize = dummyParams("resumableCurrentChunkSize").head.toInt
        val filename         = dummyParams("resumableFilename").head
        val identifier       = dummyParams("resumableIdentifier").head
        val totalSize        = dummyParams("resumableTotalSize").head.toInt
        val fc               = FileChunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
        actorRef ! new Data(actorName, fc)
        expectMsgPF() {
          case r: ConcurrentUploaderProtocol.Result => r.status must equalTo("done")
        }
      }
    }
    "receives a Progress protocol" >> {
      "and return a Result protocol" in new TestEnvironment(ActorSystem("TestSystem-04")) {
        new WithApplication {
          val actorRef    = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName   = sign(dummyParams("resumableIdentifier").head)
          val chunkNumber = dummyParams("resumableChunkNumber").head.toInt
          val status      = "done"
          actorRef ! new Progress(actorName, status, chunkNumber, self)
          expectMsgPF() {
            case r: ConcurrentUploaderProtocol.Result => r.status must equalTo("done")
          }
        }
      }
      "and returns a Result protocol when the progress is complete" in new TestEnvironment(ActorSystem("TestSystem-04")) {
        new WithApplication {

          //////////////////////////////
          // upload a chunk
          val actorRef         = system.actorOf(ConcurrentUploader.props, "Supervisor")
          val actorName        = sign(dummyParams("resumableIdentifier").head)
          val chunkNumber      = dummyParams("resumableChunkNumber").head.toInt
          val chunkSize        = dummyParams("resumableChunkSize").head.toInt
          val currentChunkSize = dummyParams("resumableCurrentChunkSize").head.toInt
          val filename         = dummyParams("resumableFilename").head
          val identifier       = dummyParams("resumableIdentifier").head
          val totalSize        = dummyParams("resumableTotalSize").head.toInt
          val fc               = FileChunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
          actorRef ! new Data(actorName, fc)

          //////////////////////////////
          // complete upload
          val status = "complete"
          actorRef ! new Progress(actorName, status, chunkNumber, self)
          expectMsgPF() {
            case r: ConcurrentUploaderProtocol.Result => r.status must equalTo("complete")
          }
        }
      }
    }
  }
}
