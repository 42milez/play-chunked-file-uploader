package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.mvc.{AnyContent, Action, Controller, RawBuffer}

import actor.{_ConcurrentUploader, ConcurrentUploader}

trait _UploadService { this: Controller =>
  val concurrentUploader: _ConcurrentUploader = ConcurrentUploader

  /** Generate a file upload form.
    *
    * @return Result
    */
  def uploadForm = Action { Ok(views.html.uploadForm()) }

  /** Check a chunk whether it has already uploaded.
    *
    * @return Future[Result]
    */
  def testBeforeUpload = Action.async { implicit request =>
    request.queryString match {
      case qs: Map[String, Seq[String]] =>
        if (qs.isEmpty) Future(BadRequest)
        else {
          concurrentUploader.checkExistenceFor(request.queryString) map {
            case true => Ok
            case false => NotFound
          }
        }
      case _ => Future(BadRequest)
    }
  }

  /** Upload a chunk.
    *
    * @return Future[Result]
    */
  def upload = Action.async { implicit request =>
    val queryString: Map[String, Seq[String]] = request.queryString
    val currentChunkSize: Int = queryString("resumableCurrentChunkSize").head.toInt

    // Concatenate chunks received
    request.body.asRaw match {
      case Some(raw: RawBuffer) =>
        raw.asBytes(currentChunkSize) match {
          case Some(bytes: Array[Byte]) =>
            concurrentUploader.concatenateFileChunk(queryString, bytes) map {
              case ("done" | "complete") => Ok
              case "error" => InternalServerError
            }
          case _ => Future { InternalServerError }
        }
      case _ => Future { BadRequest }
    }
  }
}

/** A controller which handles some file uploading operations. */
object UploadService extends Controller with _UploadService
