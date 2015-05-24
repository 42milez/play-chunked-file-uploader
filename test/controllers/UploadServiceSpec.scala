package controllers

import play.api.DefaultGlobal
import play.api.libs.iteratee._
import play.api.mvc.{Controller, Result}
import play.api.test.{FakeApplication, FakeRequest, WithApplication}
import play.api.test.Helpers._
import play.utils.UriEncoding.encodePathSegment
import org.specs2.mock._
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import actor._ConcurrentUploader

object `package` {
  def fakeUploadServiceApp() = FakeApplication(
    additionalConfiguration = inMemoryDatabase(name = "default", options = Map("DB_CLOSE_DELAY" -> "0", "MODE" -> "MYSQL")),
    withGlobal = Some(DefaultGlobal)
  )
}

class withUploadServiceApp extends WithApplication(fakeUploadServiceApp())

object UploadServiceSpec extends Specification with Mockito {

  // Query Parameters
  val params1 = Map(
    "resumableChunkNumber" -> Seq("3"),
    "resumableChunkSize" -> Seq("1048576"),
    "resumableCurrentChunkSize" -> Seq("1048576"),
    "resumableTotalSize" -> Seq("183119686"),
    "resumableType" -> Seq("application/zip"),
    "resumableIdentifier" -> Seq("183119686-datazip"),
    "resumableFilename" -> Seq("data.zip"),
    "resumableRelativePath" -> Seq("data.zip"),
    "resumableTotalChunks" -> Seq("174")
  )

  // Query Parameters with resumableCurrentChunkSize = 0
  val params2 = Map(
    "resumableChunkNumber" -> Seq("3"),
    "resumableChunkSize" -> Seq("1048576"),
    "resumableCurrentChunkSize" -> Seq("0"),
    "resumableTotalSize" -> Seq("183119686"),
    "resumableType" -> Seq("application/zip"),
    "resumableIdentifier" -> Seq("183119686-datazip"),
    "resumableFilename" -> Seq("data.zip"),
    "resumableRelativePath" -> Seq("data.zip"),
    "resumableTotalChunks" -> Seq("174")
  )

  // Query String
  val qs1 = (for { (k: String, v: Seq[String]) <- params1 } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")
  val qs2 = (for { (k: String, v: Seq[String]) <- params2 } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")

  // A Chunk
  var chunk = new Array[Byte](1024 * 1024)   // 1 MB
  scala.util.Random.nextBytes(chunk)

  def getUploadService = new _UploadService with Controller

  /** Returns a _UploadService controller which has a mock ConcurrentUploader.
    *
    * @param mock A mock of _ConcurrentUploader
    * @return _UploadService
    */
  def getUploadServiceWith(mock: _ConcurrentUploader) = new _UploadService with Controller {
    override val concurrentUploader = mock
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
    "return \"Ok\" when the chunk is already uploaded" in new withUploadServiceApp {
      val chunkUploaded = mock[_ConcurrentUploader].checkExistenceFor(params1) returns Future(true)
      val fr = FakeRequest(GET, "/upload?" + qs1)
      getUploadServiceWith(chunkUploaded).testBeforeUpload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(OK)
      }
    }
  }

  "UploadService#testBeforeUpload" should {
    "return \"NotFound\" when the chunk is not uploaded" in new withUploadServiceApp {
      val chunkNotUploaded = mock[_ConcurrentUploader].checkExistenceFor(params1) returns Future(false)
      val fr = FakeRequest(GET, "/upload?" + qs1)
      getUploadServiceWith(chunkNotUploaded).testBeforeUpload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(NOT_FOUND)
      }
    }
  }

  "UploadService#upload" should {
    "return \"Ok\" when a chunk is uploaded" in new withUploadServiceApp {
      val chunkUploaded = mock[_ConcurrentUploader].concatenateFileChunk(params1, chunk) returns Future("done")
      val fr = FakeRequest(GET, "/upload?" + qs1).withRawBody(chunk)
      getUploadServiceWith(chunkUploaded).upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(OK)
      }
    }
  }

  "UploadService#upload" should {
    "return \"Ok\" when all chunks are uploaded" in new withUploadServiceApp {
      val chunkUploaded = mock[_ConcurrentUploader].concatenateFileChunk(params1, chunk) returns Future("complete")
      val fr = FakeRequest(GET, "/upload?" + qs1).withRawBody(chunk)
      getUploadServiceWith(chunkUploaded).upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(OK)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  // IRREGULAR CASE
  //////////////////////////////////////////////////////////////////////

  "UploadService#testBeforeUpload" should {
    "return \"BadRequest\" when a Map of a query string is empty" in new withUploadServiceApp {
      getUploadService.testBeforeUpload()(FakeRequest().copy(queryString = Map.empty[String, Seq[String]])) fold asFuture match {
        case r: Future[Result] => status(r) must equalTo(BAD_REQUEST)
      }
    }
  }

  "UploadService#testBeforeUpload" should {
    "return \"BadRequest\" when a Map of a query string is null" in new withUploadServiceApp {
      getUploadService.testBeforeUpload()(FakeRequest().copy(queryString = null)) fold asFuture match {
        case r: Future[Result] => status(r) must equalTo(BAD_REQUEST)
      }
    }
  }

  "UploadService#upload" should {
    "return \"InternalServerError\" when uploading a chunk is failed" in new withUploadServiceApp {
      val chunkUploaded = mock[_ConcurrentUploader].concatenateFileChunk(params1, chunk) returns Future("error")
      val fr = FakeRequest(GET, "/upload?" + qs1).withRawBody(chunk)
      getUploadServiceWith(chunkUploaded).upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(INTERNAL_SERVER_ERROR)
      }
    }
  }

  "UploadService#upload" should {
    "return \"BadRequest\" when asRow function does not return Some(raw: RawBuffer)" in new withUploadServiceApp {
      val fr = FakeRequest(GET, "/upload?" + qs1)
      getUploadService.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(BAD_REQUEST)
      }
    }
  }

  "UploadService#upload" should {
    "return \"InternalServerError\" when asBytes function does not return Some(bytes: Array[Byte])" in new withUploadServiceApp {
      val fr = FakeRequest(GET, "/upload?" + qs2).withRawBody(chunk)
      getUploadService.upload()(fr) match {
        case r: Future[Result] => status(r) must equalTo(INTERNAL_SERVER_ERROR)
      }
    }
  }
}