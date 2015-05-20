package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.mvc.{Action, Controller, RawBuffer}

import actor.ConcurrentUpload

/** A controller which handles some file uploading operations. */
object Uploads extends Controller {

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
    ConcurrentUpload.checkExistenceFor(request.queryString) map {
      case true => Ok
      case false => NotFound
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
            ConcurrentUpload.concatenateFileChunk(queryString, bytes) map {
              case ("done" | "complete") => Ok
              case "error" => InternalServerError
            }
          case None => Future { InternalServerError }
        }
      case None => Future { BadRequest }
    }
  }
}
