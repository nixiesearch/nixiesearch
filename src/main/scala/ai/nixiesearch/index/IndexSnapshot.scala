package ai.nixiesearch.index

import ai.nixiesearch.config.mapping.IndexMapping
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.store.{Directory, IOContext, MMapDirectory}
import cats.effect.IO
import ai.nixiesearch.index.IndexSnapshot.IndexFile
import fs2.io.file.Files
import fs2.Stream

import java.nio.file.Paths
import java.io.FileInputStream
import java.io.File
import org.apache.commons.codec.digest.DigestUtils
import fs2.io.file.Path
import io.circe.Encoder
import io.circe.generic.semiauto.*
import io.circe.Decoder
import org.apache.commons.codec.binary.Hex

import java.security.MessageDigest

case class IndexSnapshot(mapping: IndexMapping, files: List[IndexFile])

object IndexSnapshot {
  case class IndexFile(name: String, size: Long, md5: String)

  import IndexMapping.json.given
  import ai.nixiesearch.config.FieldSchema.json.given

  given indexFileEncoder: Encoder[IndexFile]         = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile]         = deriveDecoder
  given indexSnapshotEncoder: Encoder[IndexSnapshot] = deriveEncoder
  given indexSnapshotDecoder: Decoder[IndexSnapshot] = deriveDecoder

  def fromDirectory(mapping: IndexMapping, dir: Directory): IO[IndexSnapshot] = {
    val files = for {
      file <- Stream.evalSeq(IO(dir.listAll().toList))
      size <- Stream.eval(IO(dir.fileLength(file).toInt))
      hash <- Stream.eval(md5(dir, file))
    } yield {
      IndexFile(name = file, size = size, md5 = hash)
    }
    files.compile.toList.map(list => IndexSnapshot(mapping, list))
  }

  val READ_BUF_SIZE_BYTES = 64 * 1024
  def md5(dir: Directory, file: String): IO[String] = IO {
    val digest   = MessageDigest.getInstance("MD5")
    val input    = dir.openInput(file, IOContext.READ)
    val fileSize = dir.fileLength(file)

    val buffer = new Array[Byte](READ_BUF_SIZE_BYTES)
    var chunk  = 0L
    while ((chunk * buffer.length) < fileSize) {
      val len = math.min(buffer.length, fileSize - chunk * buffer.length).toInt
      input.readBytes(buffer, 0, len)
      digest.update(buffer, 0, len)
      chunk += 1
    }
    Hex.encodeHexString(digest.digest())
  }
}
