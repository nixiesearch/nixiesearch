package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.util.source.SourceReader.SourceLocation.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import java.io.FileOutputStream
import java.nio.file.Files

class URLReaderTest extends AnyFlatSpec with Matchers {
  it should "read local file" in {
    val data   = List(1, 2, 3, 4).map(_.toByte)
    val path   = Files.createTempFile("nixie_", ".tmp")
    val stream = new FileOutputStream(path.toFile)
    stream.write(data.toArray)
    stream.close()
    val read = URLReader.bytes(FileLocation(LocalURL(path))).compile.toList.unsafeRunSync()
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
    val read = URLReader.bytes(DirLocation(LocalURL(dir))).compile.toList.unsafeRunSync()
    read shouldBe List.concat(data, data, data, data)
  }
}
