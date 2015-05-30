package controllers

import play.api.mvc.{Action, Controller, RawBuffer}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import service.ConcurrentUploadService

trait UploadComponent { this: Controller =>
  val uploadService: ConcurrentUploadService

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
          uploadService.checkExistenceFor(request.queryString) map {
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
            uploadService.concatenateFileChunk(queryString, bytes) map {
              case ("done" | "complete") => Ok
              case "error" => InternalServerError
            }
          case _ => Future { InternalServerError }
        }
      case _ => Future { BadRequest }
    }
  }
}

/** A controller which handles the file uploading operations. */
object Upload extends UploadComponent with Controller {
  val uploadService = new ConcurrentUploadService
}
