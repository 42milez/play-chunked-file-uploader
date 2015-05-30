package helper

object ResumableHelper {
  lazy val dummyParams = Map(
    "resumableChunkNumber" -> Seq("1"),
    "resumableChunkSize" -> Seq("1048576"),
    "resumableCurrentChunkSize" -> Seq("1048576"),
    "resumableTotalSize" -> Seq("183119686"),
    "resumableType" -> Seq("application/zip"),
    "resumableIdentifier" -> Seq("183119686-datazip"),
    "resumableFilename" -> Seq("data.zip"),
    "resumableRelativePath" -> Seq("data.zip"),
    "resumableTotalChunks" -> Seq("174"))

  lazy val dummyChunk = {
    val bytes = new Array[Byte](1024 * 1024) // 1 MB
    scala.util.Random.nextBytes(bytes)
    bytes
  }
}
