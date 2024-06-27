package ai.nixiesearch.index.store

import ai.nixiesearch.config.mapping.IndexName
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
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{Document, StringField}
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, SegmentInfos}
import org.apache.lucene.util.IOUtils

import java.nio.ByteBuffer
import java.time.Instant
import scala.util.Random
import scala.jdk.CollectionConverters.*

trait StateClientSuite[T <: StateClient] extends AnyFlatSpec with Matchers {
  def client(): Resource[IO, T]

  protected def withClient[O](code: StateClient => O): Unit = {
    val (clientInstance, shutdownHook) = client().allocated.unsafeRunSync()
    try {
      code(clientInstance)
    } finally {
      shutdownHook.unsafeRunSync()
    }
  }

  it should "return none on empty manifest" in withClient { client =>
    client.readManifest().unsafeRunSync() shouldBe None
  }

  it should "read existing manifest" in withClient { client =>
    {
      val manifest = IndexManifest(TestIndexMapping(), List(IndexFile("foo", 1L)), 0L)
      val mfjson   = manifest.asJson.spaces2.getBytes()
      client.write(IndexManifest.MANIFEST_FILE_NAME, Stream.emits(mfjson)).unsafeRunSync()
      val decoded = client.readManifest().unsafeRunSync()
      decoded shouldBe Some(manifest)
    }
  }

  it should "stream write+reads" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test1.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      val decoded = client.read("test1.bin").compile.to(Array).unsafeRunSync()
      source sameElements decoded
    }
  }

  it should "delete files" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test2.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.delete("test2.bin").unsafeRunSync()
      a[FileMissingError] shouldBe thrownBy { client.read("test2.bin").compile.drain.unsafeRunSync() }
    }
  }

  it should "fail on double delete" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test3.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.delete("test3.bin").unsafeRunSync()
      a[FileMissingError] shouldBe thrownBy {
        client.delete("test3.bin").unsafeRunSync()
      }
    }
  }

  it should "do file overwrite" in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test4.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.write("test4.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
    }
  }

  it should "create manifest from dir" in withClient { client =>
    {
      val dir    = new ByteBuffersDirectory()
      val writer = new IndexWriter(dir, new IndexWriterConfig())
      for {
        _ <- 0 until 1000
      } {
        val doc = new Document()
        doc.add(new StringField("title", "test", Store.YES))
        writer.addDocument(doc)
      }
      writer.commit()
      writer.forceMerge(1)
      writer.close()
      val byteClient = new DirectoryStateClient(dir, IndexName("test"))
      for {
        file <- SegmentInfos.readLatestCommit(dir).files(true).asScala.toList
      } {
        client.write(file, byteClient.read(file)).unsafeRunSync()
      }

      val mf  = client.createManifest(TestIndexMapping(), 0L).unsafeRunSync()
      val now = Instant.now().toEpochMilli
      mf.copy(files = mf.files.map(_.copy(updated = now)).sortBy(_.name)) shouldBe IndexManifest(
        mapping = TestIndexMapping(),
        files = List(
          IndexFile("_0.cfe", now),
          IndexFile("_0.cfs", now),
          IndexFile("_0.si", now),
          IndexFile("segments_1", now)
        ),
        seqnum = 0L
      )
    }
  }

}
