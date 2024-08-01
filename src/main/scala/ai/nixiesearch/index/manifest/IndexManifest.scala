package ai.nixiesearch.index.manifest

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest.{ChangedFileOp, IndexFile}
import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}

case class IndexManifest(mapping: IndexMapping, files: List[IndexFile], seqnum: Long) extends Logging {
  def syncFiles(): List[String] = files.map(_.name) :+ IndexManifest.MANIFEST_FILE_NAME
  def diff(target: Option[IndexManifest]): IO[List[ChangedFileOp]] = {
    IO {
      val sourceMap = files.map(f => f.name -> f.size).toMap
      val destMap   = target.map(_.files.map(f => f.name -> f.size).toMap).getOrElse(Map.empty)
      val allKeys   = (sourceMap.keySet ++ destMap.keySet ++ Set(IndexManifest.MANIFEST_FILE_NAME)).toList
      val result = for {
        key <- allKeys
        sourceTimeOption = sourceMap.get(key)
        destTimeOption   = destMap.get(key)
      } yield {
        (sourceTimeOption, destTimeOption) match {
          case (_, _) if key == IndexManifest.MANIFEST_FILE_NAME            => Some(ChangedFileOp.Add(key))
          case (Some(sourceSize), Some(destSize)) if sourceSize == destSize => None
          case (Some(_), Some(_))                                           => Some(ChangedFileOp.Add(key))
          case (Some(_), None)                                              => Some(ChangedFileOp.Add(key))
          case (None, Some(_))                                              => Some(ChangedFileOp.Del(key))
          case (None, None)                                                 => None
        }
      }
      val ops = result.flatten
      logger.debug(s"source files=$files")
      logger.debug(s"dest files=${target.map(_.files)}")
      logger.debug(s"manifest diff: ${ops}")
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
    case Add(fileName: String) extends ChangedFileOp
    case Del(fileName: String) extends ChangedFileOp
  }
}
