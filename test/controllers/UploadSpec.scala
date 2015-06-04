package controllers

import play.api.libs.iteratee.{Input, Step}
import play.api.mvc.{Controller, Result}
import play.api.test.{FakeRequest, WithApplication}
import play.api.test.Helpers.{BAD_REQUEST, INTERNAL_SERVER_ERROR, GET, NOT_FOUND, OK, status}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import helper.ResumableHelper.{chunkFirst, chunkSecond, chunkLast}
import helper.ResumableHelper.{chunkFirstQS, chunkSecondQS, chunkLastQS, chunkLengthZeroQS}
import helper.ResumableHelper.dummyChunk
import services.ConcurrentUpload

class UploadSpec extends Specification with Mockito {
  implicit private val timeout: akka.util.Timeout = 1 second

  object TestUpload extends UploadComponent with Controller with Mockito {
    val uploadService = mock[ConcurrentUpload]
    uploadService.checkExistenceFor(chunkFirst) returns Future(true)
    uploadService.checkExistenceFor(chunkSecond) returns Future(false)
    uploadService.concatenateFileChunk(chunkFirst, dummyChunk) returns Future("done")
    uploadService.concatenateFileChunk(chunkSecond, dummyChunk) returns Future("error")
    uploadService.concatenateFileChunk(chunkLast, dummyChunk) returns Future("complete")
  }

  /** According to the condition of a Iteratee, this function defines what to do next.
    * If the condition is Done, this function returns a result of a controller as Future[B].
    *
    * @param step An object which shows the current condition of a Iteratee
    * @return Future[B]
    */
  def asFuture[B](step: Step[Array[Byte], B]): Future[B] = step match {
    case Step.Done(a0, e) => Future(a0)
    case Step.Cont(k) => k(Input.EOF) fold {
      case Step.Done(a1, _) => Future.successful(a1)
      case _ => throw new Exception("Erroneous or diverging iteratee")
    }
    case Step.Error(msg, e) => throw new Exception("Erroneous iteratee: " + msg)
  }

  //////////////////////////////////////////////////////////////////////
  // REGULAR CASE
  //////////////////////////////////////////////////////////////////////

  "UploadService#testBeforeUpload" should {
    "return \"Ok\" when the chunk was already uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkFirstQS)
      TestUpload.testBeforeUpload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(OK)
      }
    }
    "return \"NotFound\" when the chunk is not uploaded yet" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkSecondQS)
      TestUpload.testBeforeUpload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(NOT_FOUND)
      }
    }
  }

  "UploadService#upload" should {
    "return \"Ok\" when a chunk is uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkFirstQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(OK)
      }
    }
    "return \"Ok\" when all chunks are uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkLastQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(OK)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  // IRREGULAR CASE
  //////////////////////////////////////////////////////////////////////

  "UploadService#testBeforeUpload" should {
    "return \"BadRequest\" when a Map of a query string is empty" in new WithApplication {
      TestUpload.testBeforeUpload()(FakeRequest().copy(queryString = Map.empty[String, Seq[String]])) fold asFuture match {
        case result: Future[Result] => status(result) must equalTo(BAD_REQUEST)
      }
    }
    "return \"BadRequest\" when a Map of a query string is null" in new WithApplication {
      TestUpload.testBeforeUpload()(FakeRequest().copy(queryString = null)) fold asFuture match {
        case result: Future[Result] => status(result) must equalTo(BAD_REQUEST)
      }
    }
  }

  "UploadService#upload" should {
    "return \"InternalServerError\" when uploading a chunk is failed" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkSecondQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(INTERNAL_SERVER_ERROR)
      }
    }
    "return \"BadRequest\" when asRow function does not return Some(raw: RawBuffer)" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkFirstQS)
      TestUpload.upload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(BAD_REQUEST)
      }
    }
    "return \"InternalServerError\" when asBytes function does not return Some(bytes: Array[Byte])" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkLengthZeroQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case result: Future[Result] => status(result) must equalTo(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
