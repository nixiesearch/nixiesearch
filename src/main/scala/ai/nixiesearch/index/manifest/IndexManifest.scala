package ai.nixiesearch.index.manifest

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}

case class IndexManifest(mapping: IndexMapping, files: List[IndexFile], version: Long)

object IndexManifest {
  val MANIFEST_FILE_NAME = "index.json"

  import IndexMapping.json.given

  given indexManifestEncoder: Encoder[IndexManifest] = deriveEncoder
  given indexManifestDecoder: Decoder[IndexManifest] = deriveDecoder

  given indexFileEncoder: Encoder[IndexFile] = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile] = deriveDecoder

  case class IndexFile(name: String, size: Int)

}
