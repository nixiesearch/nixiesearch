package ai.nixiesearch.index

import ai.nixiesearch.config.mapping.IndexMapping
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.store.MMapDirectory
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
import io.circe.generic.semiauto._
import io.circe.Decoder

case class IndexSnapshot(mapping: IndexMapping, files: List[IndexFile])

object IndexSnapshot {
  case class IndexFile(name: String, size: Long, md5: String, updated: Long)

  import IndexMapping.json.given
  import ai.nixiesearch.config.FieldSchema.json.given

  given indexFileEncoder: Encoder[IndexFile]         = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile]         = deriveDecoder
  given indexSnapshotEncoder: Encoder[IndexSnapshot] = deriveEncoder
  given indexSnapshotDecoder: Decoder[IndexSnapshot] = deriveDecoder

  def fromDirectory(mapping: IndexMapping, dir: MMapDirectory): IO[IndexSnapshot] = {
    val files = for {
      root    <- Stream.eval(IO(dir.getDirectory()))
      file    <- Stream.evalSeq(IO(dir.listAll().toList))
      path    <- Stream(Path(root.toString() + "/" + file))
      size    <- Stream.eval(Files[IO].size(path))
      updated <- Stream.eval(Files[IO].getLastModifiedTime(path))
      md5 <- Stream
        .bracket(IO(new FileInputStream(new File(s"$root/$file"))))(is => IO(is.close()))
        .map(is => DigestUtils.md5Hex(is))
    } yield {
      IndexFile(name = file, size = size, md5 = md5, updated = updated.toMillis)
    }
    files.compile.toList.map(list => IndexSnapshot(mapping, list))
  }

}
