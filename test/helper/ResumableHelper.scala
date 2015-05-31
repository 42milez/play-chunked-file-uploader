package helper

import play.utils.UriEncoding.encodePathSegment

object ResumableHelper {
  lazy val chunkFirst = Map(
    "resumableChunkNumber" -> Seq("1"),
    "resumableChunkSize" -> Seq("1048576"),
    "resumableCurrentChunkSize" -> Seq("1048576"),
    "resumableTotalSize" -> Seq("183119686"),
    "resumableType" -> Seq("application/zip"),
    "resumableIdentifier" -> Seq("183119686-datazip"),
    "resumableFilename" -> Seq("data.zip"),
    "resumableRelativePath" -> Seq("data.zip"),
    "resumableTotalChunks" -> Seq("174"))
  lazy val chunkSecond = chunkFirst + ("resumableChunkNumber" -> Seq("2"))
  lazy val chunkLast = chunkFirst + ("resumableChunkNumber" -> Seq("174"))
  lazy val chunkLengthZero = chunkFirst + ("resumableCurrentChunkSize" -> Seq("0"))

  lazy val chunkFirstQS = (for { (k: String, v: Seq[String]) <- chunkFirst } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")
  lazy val chunkSecondQS = (for { (k: String, v: Seq[String]) <- chunkSecond } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")
  lazy val chunkLastQS = (for { (k: String, v: Seq[String]) <- chunkLast } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")
  lazy val chunkLengthZeroQS = (for { (k: String, v: Seq[String]) <- chunkLengthZero } yield k + "=" + encodePathSegment(v.head, "UTF-8")).mkString("&")

  lazy val dummyChunk = {
    val bytes = new Array[Byte](1024 * 1024) // 1 MB
    scala.util.Random.nextBytes(bytes)
    bytes
  }
}
