package ai.nixiesearch.index.store

import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.StateClient.StateError.{FileExistsError, FileMissingError}
import ai.nixiesearch.util.TestIndexMapping
import cats.effect.{IO, Resource}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.apache.lucene.store.ByteBuffersDirectory
import io.circe.syntax.*

import java.nio.file.NoSuchFileException
import fs2.{Chunk, Collector, Stream}

import java.nio.ByteBuffer
import scala.util.Random

trait StateClientSuite[T <: StateClient] extends AnyFlatSpec with Matchers {
  def client(): T

  protected def withClient[O](code: StateClient => O): Unit = {
    val clientInstance = client()
    try {
      code(clientInstance)
    } finally {
      clientInstance.close().unsafeRunSync()
    }
  }

  it should "fail on empty manifest" in withClient { client =>
    a[FileMissingError] should be thrownBy client.manifest().unsafeRunSync()
  }

  it should "read existing manifest" in withClient { client =>
    {
      val manifest = IndexManifest(TestIndexMapping(), List(IndexFile("foo", 1L, 1L)), 0L)
      val mfjson   = manifest.asJson.spaces2.getBytes()
      client.write(IndexManifest.MANIFEST_FILE_NAME, Stream.emits(mfjson)).unsafeRunSync()
      val decoded = client.manifest().unsafeRunSync()
      decoded shouldBe manifest
    }
  }

  it should "stream write+reads" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      val decoded = client.read("test.bin").compile.to(Array).unsafeRunSync()
      source sameElements decoded
    }
  }

  it should "delete files" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.delete("test.bin").unsafeRunSync()
      a[FileMissingError] shouldBe thrownBy { client.read("test.bin").compile.drain.unsafeRunSync() }
    }
  }

  it should "fail on double delete" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.delete("test.bin").unsafeRunSync()
      a[FileMissingError] shouldBe thrownBy {
        client.delete("test.bin").unsafeRunSync()
      }
    }
  }

  it should "fail on file overwrite" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      a[FileExistsError] shouldBe thrownBy {
        client.write("test.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      }
    }
  }

}
