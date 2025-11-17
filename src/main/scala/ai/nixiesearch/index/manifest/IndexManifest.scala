package ai.nixiesearch.index.manifest

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest.{ChangedFileOp, IndexFile}
import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}

case class IndexManifest(mapping: IndexMapping, files: List[IndexFile], seqnum: Long) extends Logging {
  def diff(target: Option[IndexManifest]): IO[List[ChangedFileOp]] = {
    IO {
      val sourceMap = files.map(f => f.name -> f.size).toMap
      val destMap   = target.map(_.files.map(f => f.name -> f.size).toMap).getOrElse(Map.empty)
      val allKeys   = (sourceMap.keySet ++ destMap.keySet ++ Set(IndexManifest.MANIFEST_FILE_NAME)).toList
      val result    = for {
        key <- allKeys
        sourceSizeOption = sourceMap.get(key)
        destSizeOption   = destMap.get(key)
      } yield {
        (sourceSizeOption, destSizeOption) match {
          case (_, _) if key == IndexManifest.MANIFEST_FILE_NAME            => Some(ChangedFileOp.Add(key, None))
          case (Some(sourceSize), Some(destSize)) if sourceSize == destSize => None
          case (Some(sourceSize), Some(_)) => Some(ChangedFileOp.Add(key, Some(sourceSize)))
          case (Some(sourceSize), None)    => Some(ChangedFileOp.Add(key, Some(sourceSize)))
          case (None, Some(_))             => Some(ChangedFileOp.Del(key))
          case (None, None)                => None
        }
      }
      val ops = result.flatten
      logger.info(s"Source files = $files")
      logger.info(s"Destination files = ${target.map(_.files)}")
      logger.info(s"Manifest diff: ${ops}.")
      ops
    }
  }

}

object IndexManifest extends Logging {
  val MANIFEST_FILE_NAME = "index.json"

  import IndexMapping.json.given

  given indexManifestEncoder: Encoder[IndexManifest] = deriveEncoder
  given indexManifestDecoder: Decoder[IndexManifest] = deriveDecoder

  given indexFileEncoder: Encoder[IndexFile] = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile] = deriveDecoder

  case class IndexFile(name: String, size: Long)

  enum ChangedFileOp {
    case Add(fileName: String, size: Option[Long]) extends ChangedFileOp
    case Del(fileName: String)                     extends ChangedFileOp
  }
}
