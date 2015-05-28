package controllers

import play.api.libs.iteratee._
import play.api.mvc.{Controller, Result}
import play.api.test.{FakeRequest, WithApplication}
import play.api.test.Helpers._
import play.utils.UriEncoding.encodePathSegment
import org.specs2.mock._
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UploadServiceSpec extends Specification with Mockito {
  import actor.ConcurrentUploadService

  val dummyParams = Map(
    "resumableChunkNumber" -> Seq("0"),
    "resumableChunkSize" -> Seq("1048576"),
    "resumableCurrentChunkSize" -> Seq("1048576"),
    "resumableTotalSize" -> Seq("183119686"),
    "resumableType" -> Seq("application/zip"),
    "resumableIdentifier" -> Seq("183119686-datazip"),
    "resumableFilename" -> Seq("data.zip"),
    "resumableRelativePath" -> Seq("data.zip"),
    "resumableTotalChunks" -> Seq("174"))

  val chunkUploaded = dummyParams + ("resumableChunkNumber" -> Seq("3"))
  val chunkNotUploaded = dummyParams + ("resumableChunkNumber" -> Seq("4"))
  val chunkLengthZero = dummyParams + ("resumableCurrentChunkSize" -> Seq("0"))

  val chunkUploadedQS = (for { (k: String, v: Seq[String]) <- chunkUploaded } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")
  val chunkNotUploadedQS = (for { (k: String, v: Seq[String]) <- chunkNotUploaded } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")
  val chunkLengthZeroQS = (for { (k: String, v: Seq[String]) <- chunkLengthZero } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")

  var dummyChunk = new Array[Byte](1024 * 1024) // 1 MB
  scala.util.Random.nextBytes(dummyChunk)

  object TestUpload extends UploadComponent with Controller with Mockito {
    val uploadService = mock[ConcurrentUploadService]
    uploadService.checkExistenceFor(chunkUploaded) returns Future(true)
    uploadService.checkExistenceFor(chunkNotUploaded) returns Future(false)
    uploadService.concatenateFileChunk(chunkNotUploaded, dummyChunk) returns Future("done")
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

    "return \"Ok\" when the chunk is already uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkUploadedQS)
      TestUpload.testBeforeUpload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(OK)
      }
    }

    "return \"NotFound\" when the chunk is not uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkNotUploadedQS)
      TestUpload.testBeforeUpload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(NOT_FOUND)
      }
    }
  }

  "UploadService#upload" should {

    "return \"Ok\" when a chunk is uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkUploadedQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(OK)
      }
    }

    "return \"Ok\" when all chunks are uploaded" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkUploadedQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(OK)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  // IRREGULAR CASE
  //////////////////////////////////////////////////////////////////////

  "UploadService#testBeforeUpload" should {

    "return \"BadRequest\" when a Map of a query string is empty" in new WithApplication {
      TestUpload.testBeforeUpload()(FakeRequest().copy(queryString = Map.empty[String, Seq[String]])) fold asFuture match {
        case r: Future[Result] => status(r) must equalTo(BAD_REQUEST)
      }
    }

    "return \"BadRequest\" when a Map of a query string is null" in new WithApplication {
      TestUpload.testBeforeUpload()(FakeRequest().copy(queryString = null)) fold asFuture match {
        case r: Future[Result] => status(r) must equalTo(BAD_REQUEST)
      }
    }
  }

  "UploadService#upload" should {

    "return \"InternalServerError\" when uploading a chunk is failed" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkUploadedQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(INTERNAL_SERVER_ERROR)
      }
    }

    "return \"BadRequest\" when asRow function does not return Some(raw: RawBuffer)" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkUploadedQS)
      TestUpload.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(BAD_REQUEST)
      }
    }

    "return \"InternalServerError\" when asBytes function does not return Some(bytes: Array[Byte])" in new WithApplication {
      val fr = FakeRequest(GET, "/upload?" + chunkLengthZeroQS).withRawBody(dummyChunk)
      TestUpload.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
