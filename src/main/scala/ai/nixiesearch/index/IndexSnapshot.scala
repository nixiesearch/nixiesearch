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
  case class IndexFile(name: String, size: Long, md5: String)

  import IndexMapping.json._
  implicit val indexFileEncoder: Encoder[IndexFile]         = deriveEncoder
  implicit val indexFileDecoder: Decoder[IndexFile]         = deriveDecoder
  implicit val indexSnapshotEncoder: Encoder[IndexSnapshot] = deriveEncoder
  implicit val indexSnapshotDecoder: Decoder[IndexSnapshot] = deriveDecoder

  def fromDirectory(mapping: IndexMapping, dir: MMapDirectory): IO[IndexSnapshot] = {
    val files = for {
      root <- Stream.eval(IO(dir.getDirectory()))
      file <- Stream.evalSeq(IO(dir.listAll().toList))
      size <- Stream.eval(Files[IO].size(Path(root.toString() + "/" + file)))
      md5 <- Stream
        .bracket(IO(new FileInputStream(new File(s"$root/$file"))))(is => IO(is.close()))
        .map(is => DigestUtils.md5Hex(is))
    } yield {
      IndexFile(name = file, size = size, md5 = md5)
    }
    files.compile.toList.map(list => IndexSnapshot(mapping, list))
  }

}
