package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL.{HttpURL, LocalURL, S3URL}
import ai.nixiesearch.util.S3Client
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.io.FileOutputStream
import java.nio.file.Files
import fs2.Stream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.http4s.Uri

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
    val out = URLReader.bytes(HttpURL(Uri.unsafeFromString("https://httpbin.org/get"))).compile.toList.unsafeRunSync()
    out.size should be > (0)
  }
}
