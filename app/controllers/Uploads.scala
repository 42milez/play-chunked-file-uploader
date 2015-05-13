package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.mvc.{RawBuffer, Action, Controller}
import models.ConcurrentUpload

object Uploads extends Controller {

  def uploadForm = Action {
    Ok(views.html.uploadForm())
  }

  def testBeforeUpload = Action.async { implicit request =>
    ConcurrentUpload.checkExistenceFor(request.queryString) map {
      case true =>
        Ok
      case false =>
        NotFound
    }
  }

  def upload = Action.async { implicit request =>
    val queryString: Map[String, Seq[String]] = request.queryString
    val currentChunkSize: Int = queryString("resumableCurrentChunkSize").head.toInt

    // Concatenate chunks received
    request.body.asRaw match {
      case Some(raw: RawBuffer) =>
        raw.asBytes(currentChunkSize) match {
          case Some(bytes: Array[Byte]) =>
            ConcurrentUpload.concatenateFileChunk(queryString, bytes) map {
              case ("done" | "complete") =>
                Ok
              case "error" =>
                InternalServerError
            }
          case None =>
            Future { InternalServerError }
        }
      case None =>
        Future { BadRequest }
    }
  }
}
