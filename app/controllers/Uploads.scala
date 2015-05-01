package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.Logger
import play.api.mvc.{RawBuffer, Action, Controller}
import models.ConcurrentUpload

object Uploads extends Controller {

  def uploadForm = Action {
    Ok(views.html.uploadForm())
  }

  def testBeforeUpload = Action.async { implicit request =>
    Logger.info("Received a request: GET")
    ConcurrentUpload.checkExistenceFor(request.queryString) map {
      case true =>
        Logger.info("Chunk is exist.")
        Ok
      case false =>
        Logger.info("Chunk is not exist.")
        NotFound
    }
  }

  def upload = Action.async { implicit request =>
    Logger.info("Received a request: POST")

    // Get File Info
    val queryString: Map[String, Seq[String]] = request.queryString
    val currentChunkSize: Int = queryString("resumableCurrentChunkSize").head.toInt

    // Concatenate chunks received
    request.body.asRaw match {
      case Some(raw: RawBuffer) =>
        raw.asBytes(currentChunkSize) match {
          case Some(bytes: Array[Byte]) =>
            ConcurrentUpload.concatenateFileChunk(queryString, bytes) map {
              case ("done" | "complete") =>
                Logger.info("Upload succeeded.")
                Ok
              case "error" =>
                Logger.error("Upload failed. - Internal Server Error")
                InternalServerError
            }
          case None =>
            Logger.error("Upload failed. - Internal Server Error")
            Future { InternalServerError }
        }
      case None =>
        Logger.error("Upload failed. - BadRequest")
        Future { BadRequest }
    }
  }
}
