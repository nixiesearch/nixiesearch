package ai.nixiesearch.index.store

import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.StateClient.StateError.FileMissingError
import ai.nixiesearch.util.Tags.EndToEnd.Network
import ai.nixiesearch.util.TestIndexMapping
import cats.effect.{IO, Resource}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.apache.lucene.store.ByteBuffersDirectory
import io.circe.syntax.*
import fs2.{Chunk, Collector, Stream}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{Document, StringField}
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, SegmentInfos}

import java.nio.ByteBuffer
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

  it should "return none on empty manifest" taggedAs (Network) in withClient { client =>
    client.readManifest().unsafeRunSync() shouldBe None
  }

  it should "read existing manifest" taggedAs (Network) in withClient { client =>
    {
      val manifest = IndexManifest(TestIndexMapping(), List(IndexFile("foo", 1L)), 0L)
      val mfjson   = manifest.asJson.spaces2.getBytes()
      client.write(IndexManifest.MANIFEST_FILE_NAME, Stream.emits(mfjson)).unsafeRunSync()
      val decoded = client.readManifest().unsafeRunSync()
      decoded shouldBe Some(manifest)
    }
  }

  it should "stream write+reads" taggedAs (Network) in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test1.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      val decoded = client.read("test1.bin", None).compile.to(Array).unsafeRunSync()
      source sameElements decoded
    }
  }

  it should "delete files" taggedAs (Network) in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test2.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.delete("test2.bin").unsafeRunSync()
      a[FileMissingError] shouldBe thrownBy { client.read("test2.bin", None).compile.drain.unsafeRunSync() }
    }
  }

  it should "fail on double delete" taggedAs (Network) in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test3.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.delete("test3.bin").unsafeRunSync()
      a[FileMissingError] shouldBe thrownBy {
        client.delete("test3.bin").unsafeRunSync()
      }
    }
  }

  it should "do file overwrite" taggedAs (Network) in withClient { client =>
    {
      val source = Random.nextBytes(1024 * 1024)
      client.write("test4.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
      client.write("test4.bin", Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(source)))).unsafeRunSync()
    }
  }

  it should "create manifest from dir" taggedAs (Network) in withClient { client =>
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
        client.write(file, byteClient.read(file, None)).unsafeRunSync()
      }

      val mf = client.createManifest(TestIndexMapping(), 0L).unsafeRunSync()
      mf.copy(files = mf.files.map(_.copy(size = 0L)).sortBy(_.name)) shouldBe IndexManifest(
        mapping = TestIndexMapping(),
        files = List(
          IndexFile("_0.cfe", 0L),
          IndexFile("_0.cfs", 0L),
          IndexFile("_0.si", 0L),
          IndexFile("segments_1", 0L)
        ),
        seqnum = 0L
      )
    }
  }

}
