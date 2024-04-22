package ai.nixiesearch.index.manifest

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.NixieDirectory
import cats.effect.IO
import fs2.Stream
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import io.circe.parser.*
import org.apache.commons.codec.binary.Hex
import org.apache.lucene.store.{Directory, IOContext, MMapDirectory}

import java.security.MessageDigest

case class IndexManifest(mapping: IndexMapping, files: List[IndexFile], version: Long)

object IndexManifest {
  val MANIFEST_FILE_NAME = "index.json"

  import IndexMapping.json.given

  given indexManifestEncoder: Encoder[IndexManifest] = deriveEncoder
  given indexManifestDecoder: Decoder[IndexManifest] = deriveDecoder

  given indexFileEncoder: Encoder[IndexFile] = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile] = deriveDecoder

  case class IndexFile(name: String, size: Int)

  def fromDirectory(mapping: IndexMapping, dir: Directory): IO[IndexManifest] = {
    val files = for {
      file <- Stream.evalSeq(IO(dir.listAll().toList))
      size <- Stream.eval(IO(dir.fileLength(file).toInt))
    } yield {
      IndexFile(name = file, size = size)
    }
    for {

    }
    files.compile.toList.map(list => IndexManifest(mapping, list))
  }
  
}
