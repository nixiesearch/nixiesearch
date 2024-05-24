package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL.{HttpURL, LocalURL, S3URL}
import ai.nixiesearch.util.S3Client
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.{Host, Port}

import java.io.FileOutputStream
import java.nio.file.Files
import fs2.Stream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.http4s.{Entity, HttpRoutes, Response, Status, Uri}
import org.http4s.ember.server.EmberServerBuilder
import scodec.bits.ByteVector
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.util.Random

class URLReaderTest extends AnyFlatSpec with Matchers {
  it should "read local file" in {
    val data   = List(1, 2, 3, 4).map(_.toByte)
    val path   = Files.createTempFile("nixie_", ".tmp")
    val stream = new FileOutputStream(path.toFile)
    stream.write(data.toArray)
    stream.close()
    val read = URLReader.bytes(LocalURL(path)).compile.toList.unsafeRunSync()
    read shouldBe data
  }

  it should "read local compressed files" in {
    val data   = List(1, 2, 3, 4).map(_.toByte)
    val path   = Files.createTempFile("nixie_", ".tmp.gz")
    val stream = new GzipCompressorOutputStream(new FileOutputStream(path.toFile))
    stream.write(data.toArray)
    stream.close()
    val read = URLReader.bytes(LocalURL(path)).compile.toList.unsafeRunSync()
    read shouldBe data
  }

  it should "read local dirs" in {
    val data = List(1, 2, 3, 4).map(_.toByte)
    val dir  = Files.createTempDirectory("nixie_")
    (0 until 4).foreach(i => {
      val filePath = Files.createTempFile(dir, s"${i}_", ".tmp")
      val stream   = new FileOutputStream(filePath.toFile)
      stream.write(data.toArray)
      stream.close()
    })
    val read = URLReader.bytes(LocalURL(dir), true).compile.toList.unsafeRunSync()
    read shouldBe List.concat(data, data, data, data)
  }

  it should "read s3 files" in {
    val data             = List(1, 2, 3, 4).map(_.toByte)
    val name             = Random.nextInt(1024000).toString + ".tmp"
    val client: S3Client = S3Client.create("us-east-1", Some("http://localhost:4566")).allocated.unsafeRunSync()._1
    client.multipartUpload("bucket", name, Stream.emits(data)).unsafeRunSync()
    client.client.close()
    val out = URLReader
      .bytes(S3URL("bucket", name, Some("us-east-1"), Some("http://localhost:4566")))
      .compile
      .toList
      .unsafeRunSync()
    out shouldBe data
  }

  it should "read s3 dirs" in {
    val data             = List(1, 2, 3, 4).map(_.toByte)
    val name             = Random.nextInt(1024000).toString + "_dir"
    val client: S3Client = S3Client.create("us-east-1", Some("http://localhost:4566")).allocated.unsafeRunSync()._1
    (0 until 4).foreach(i => {
      client.multipartUpload("bucket", s"$name/$i.tmp", Stream.emits(data)).unsafeRunSync()
    })
    client.client.close()
    val out = URLReader
      .bytes(S3URL("bucket", name, Some("us-east-1"), Some("http://localhost:4566")), recursive = true)
      .compile
      .toList
      .unsafeRunSync()
    out shouldBe List.concat(data, data, data, data)
  }

  it should "read http files" in {
    implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
    val data                                = "hello".getBytes()
    val route = HttpRoutes.of[IO] { case req @ GET -> Root / "file.json" =>
      IO(Response[IO](status = Status.Ok, entity = Entity.strict(ByteVector(data))))
    }
    val (server, shutdown) = EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(18080).get)
      .withHttpApp(Router("/" -> route).orNotFound)
      .build
      .allocated
      .unsafeRunSync()
    val out =
      URLReader.bytes(HttpURL(Uri.unsafeFromString("http://127.0.0.1:18080/file.json"))).compile.toList.unsafeRunSync()
    out shouldBe data.toList
    shutdown.unsafeRunSync()
  }
}
