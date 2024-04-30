package ai.nixiesearch.index.store

import ai.nixiesearch.config.StoreConfig.StoreUrl.S3StoreUrl
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.store.s3.WriteOp
import ai.nixiesearch.index.store.s3.WriteOp.Delete
import cats.effect.IO
import org.apache.lucene.store.{Directory, FilterDirectory, IOContext}

import scala.jdk.CollectionConverters.*
import java.nio.file.Path
import java.util
import java.util.concurrent.ConcurrentLinkedQueue

case class RemoteSyncDirectory(local: Directory, queue: ConcurrentLinkedQueue[WriteOp])
    extends FilterDirectory(local)
    with Logging {
  override def sync(names: util.Collection[String]): Unit = {
    val pendingDeletions = getPendingDeletions.asScala.toList
    val filesToSync      = names.asScala.toList
    super.sync(names)
    logger.debug(s"sync: files=$filesToSync deletes=$pendingDeletions")
    filesToSync.foreach(file => queue.add(WriteOp.Put(file)))
    pendingDeletions.foreach(file => queue.add(WriteOp.Delete(file)))
  }
}

object RemoteSyncDirectory {
  def init(url: S3StoreUrl, path: Path): IO[RemoteSyncDirectory] = {
    ???
  }
}
