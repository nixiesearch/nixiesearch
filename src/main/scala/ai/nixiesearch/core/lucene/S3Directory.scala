package ai.nixiesearch.core.lucene

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.apache.lucene.store.{Directory, IOContext, IndexInput, IndexOutput, Lock}

import java.util

class S3Directory extends Directory {
  override def listAll(): Array[String] = ???

  override def deleteFile(name: String): Unit = ???

  override def fileLength(name: String): Long = ???

  override def createOutput(name: String, context: IOContext): IndexOutput = ???

  override def createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput = ???

  override def sync(names: util.Collection[String]): Unit = ???

  override def syncMetaData(): Unit = ???

  override def rename(source: String, dest: String): Unit = ???

  override def openInput(name: String, context: IOContext): IndexInput = ???

  override def obtainLock(name: String): Lock = ???

  override def close(): Unit = IO.unit.unsafeRunSync()(IORuntime.builder().build())

  override def getPendingDeletions: util.Set[String] = ???
}

object S3Directory {
  class S3IndexOutput(resourceDescription: String, name: String) extends IndexOutput(resourceDescription, name) {
    override def writeByte(b: Byte): Unit = ???

    override def writeBytes(b: Array[Byte], offset: Int, length: Int): Unit = ???

    override def getChecksum: Long = ???

    override def getFilePointer: Long = ???

    override def close(): Unit = ???
  }

  class S3IndexInput(resourceDescription: String, bufferSize: Int) extends IndexInput(resourceDescription) {
    override def readByte(): Byte = ???

    override def readBytes(b: Array[Byte], offset: Int, len: Int): Unit = ???

    override def getFilePointer: Long = ???

    override def length(): Long = ???

    override def seek(pos: Long): Unit = ???

    override def slice(sliceDescription: String, offset: Long, length: Long): IndexInput = ???

    override def close(): Unit = ???
  }

}
