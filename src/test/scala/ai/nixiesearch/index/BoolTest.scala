package ai.nixiesearch.index

import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.{OnnxBiEncoder, OnnxSession}
import org.apache.lucene.document.{Document, KnnFloatVectorField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, VectorSimilarityFunction}
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, IndexSearcher}
import org.apache.lucene.store.MMapDirectory
import cats.effect.unsafe.implicits.global
import java.nio.file.Files

object BoolTest {
  def main(args: Array[String]): Unit = {
    val dir     = new MMapDirectory(Files.createTempDirectory("woo"))
    val writer  = new IndexWriter(dir, new IndexWriterConfig())
    val handle  = HuggingFaceHandle("nixiesearch", "all-MiniLM-L6-v2-onnx")
    val session = OnnxSession.load(handle, modelFile = "model.onnx").unsafeRunSync()
    val enc     = OnnxBiEncoder(session)
    val embeds = enc.embed(
      Array(
        "berlin is a capital of germany",
        "main train station in berlin is crowded",
        "in berlin doner was invented",
        "i have no idea what I'm doing",
        "where kebab was invented?"
      )
    )

    val doc1 = new Document()
    doc1.add(KnnFloatVectorField("emb1", embeds(0), VectorSimilarityFunction.COSINE))
    doc1.add(KnnFloatVectorField("emb2", embeds(1), VectorSimilarityFunction.COSINE))
    val doc2 = new Document()
    doc2.add(KnnFloatVectorField("emb1", embeds(2), VectorSimilarityFunction.COSINE))
    doc2.add(KnnFloatVectorField("emb2", embeds(3), VectorSimilarityFunction.COSINE))

    writer.addDocument(doc1)
    writer.addDocument(doc2)
    writer.commit()
    val reader = DirectoryReader.open(writer)
    val search = new IndexSearcher(reader)
    val query  = new BooleanQuery.Builder()
    query.add(new BooleanClause(KnnFloatVectorField.newVectorQuery("emb1", embeds(4), 10), Occur.SHOULD))
    query.add(new BooleanClause(KnnFloatVectorField.newVectorQuery("emb2", embeds(4), 10), Occur.SHOULD))
    val docs = search.search(query.build(), 10)
    val br   = 1
  }
}
