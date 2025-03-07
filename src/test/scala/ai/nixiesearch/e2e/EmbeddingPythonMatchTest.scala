package ai.nixiesearch.e2e

import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.IOUtils
import org.scalatest.prop.TableDrivenPropertyChecks.forAll

import java.nio.charset.StandardCharsets

class EmbeddingPythonMatchTest extends AnyFlatSpec with Matchers {
  lazy val embeds = loadTable("/embeds/embeds.csv")

  def loadTable(path: String) = {
    val csv = IOUtils
      .toString(this.getClass.getResourceAsStream(path), StandardCharsets.UTF_8)
      .split('\n')
      .toList
      .map(line => {
        val tokens = line.split(',')
        val embed  = tokens(2).split(' ').map(_.toFloat)
        (tokens(0), tokens(1), embed)
      })
    Table(("model", "text", "embedding"), csv*)
  }

  it should "text" taggedAs (EndToEnd.Embeddings) in {
    forAll(embeds) { (model, text, embed) =>
      {
        val result = EmbeddingInferenceEndToEndTest.embed(model, text)
        val rmse   = math.sqrt(result.zip(embed).map((a, b) => (a - b) * (a - b) / result.length).sum)
        rmse should be < 0.001
      }
    }

  }
}
