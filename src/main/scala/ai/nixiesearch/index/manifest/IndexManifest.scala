package ai.nixiesearch.index.manifest

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import cats.effect.{IO, Resource}
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import io.circe.parser.*
import org.apache.lucene.store.{Directory, IOContext}
import fs2.Stream

case class IndexManifest(mapping: IndexMapping, files: List[IndexFile], seqnum: Long)

object IndexManifest extends Logging {
  val MANIFEST_FILE_NAME = "index.json"

  import IndexMapping.json.given

  given indexManifestEncoder: Encoder[IndexManifest] = deriveEncoder
  given indexManifestDecoder: Decoder[IndexManifest] = deriveDecoder

  given indexFileEncoder: Encoder[IndexFile] = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile] = deriveDecoder

  case class IndexFile(name: String, updated: Long)

  enum ChangedFileOp {
    case Add(fileName: String) extends ChangedFileOp
    case Del(fileName: String) extends ChangedFileOp
  }
  def diff(source: IndexManifest, dest: IndexManifest): List[ChangedFileOp] = {
    val sourceFiles               = source.files.map(_.name)
    val destFiles                 = dest.files.map(_.name)
    val adds: List[ChangedFileOp] = sourceFiles.filter(f => !destFiles.contains(f)).map(f => ChangedFileOp.Add(f))
    val dels: List[ChangedFileOp] = destFiles.filter(f => !sourceFiles.contains(f)).map(f => ChangedFileOp.Del(f))
    adds ++ dels
  }

  def diff(source: IndexManifest): List[ChangedFileOp] =
    source.files.map(f => ChangedFileOp.Add(f.name))

}
