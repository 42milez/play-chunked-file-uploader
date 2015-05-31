package service

import akka.actor.ActorSystem
import org.specs2.mutable.Specification
import play.api.test.WithApplication
import scala.concurrent.ExecutionContext.Implicits.global

import actors.ConcurrentUploader
import helper.AkkaHelper.TestEnvironment
import helper.ResumableHelper.{chunkFirst, dummyChunk}

class ConcurrentUploadSpec extends Specification {
  class TestConcurrentUpload(implicit system: ActorSystem) extends ConcurrentUploadComponent with UploadComponent {
    val supervisor = system.actorOf(ConcurrentUploader.props)
  }

  "ConcurrentUploader#checkExistenceFor" should {
    "return \"false\" when the chunk is not uploaded yet" in new TestEnvironment(ActorSystem("TestSystem-01")) {
      new WithApplication {
        val concurrentUploadService = new TestConcurrentUpload
        concurrentUploadService.checkExistenceFor(chunkFirst) map {
          case result: Boolean =>
            result must equalTo(false)
        }
      }
    }
    "return \"true\" when the chunk is already uploaded" in new TestEnvironment(ActorSystem("TestSystem-02")) {
      new WithApplication {
        val concurrentUploadService = new TestConcurrentUpload
        concurrentUploadService.concatenateFileChunk(chunkFirst, dummyChunk) map {
          case result1: String =>
            result1 must equalTo("done")
            concurrentUploadService.checkExistenceFor(chunkFirst) map {
              case result2: Boolean =>
                result2 must equalTo(true)
            }
        }
      }
    }
  }
}
