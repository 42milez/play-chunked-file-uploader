package service

import akka.actor.ActorSystem
import org.specs2.mutable.Specification
import play.api.test.WithApplication
import scala.concurrent.ExecutionContext.Implicits.global

import actor.ConcurrentUploader
import helper.AkkaHelper.TestEnvironment
import helper.ResumableHelper.{dummyParams, dummyChunk}

class ConcurrentUploadSpec extends Specification {
  class TestConcurrentUploadService(implicit system: ActorSystem) extends ConcurrentUploadServiceComponent with UploadServiceComponent {
    val supervisor = system.actorOf(ConcurrentUploader.props)
  }

  //////////////////////////////////////////////////////////////////////
  // REGULAR CASE
  //////////////////////////////////////////////////////////////////////

  "ConcurrentUploader#checkExistenceFor" should {
    "return \"false\" when the chunk is not uploaded yet" in new TestEnvironment(ActorSystem("TestSystem-01")) {
      new WithApplication {
        val concurrentUploadService = new TestConcurrentUploadService
        concurrentUploadService.checkExistenceFor(dummyParams) map {
          case r: Boolean =>
            r must equalTo(false)
        }
      }
    }
    "return \"true\" when the chunk is already uploaded" in new TestEnvironment(ActorSystem("TestSystem-02")) {
      new WithApplication {
        val concurrentUploadService = new TestConcurrentUploadService
        concurrentUploadService.concatenateFileChunk(dummyParams, dummyChunk) map {
          case r1: String =>
            r1 must equalTo("done")
            concurrentUploadService.checkExistenceFor(dummyParams) map {
              case r2: Boolean =>
                r2 must equalTo(true)
            }
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  // IRREGULAR CASE
  //////////////////////////////////////////////////////////////////////

  // ...
}
