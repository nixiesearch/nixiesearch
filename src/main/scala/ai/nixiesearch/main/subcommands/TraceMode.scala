package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.retrieve.SemanticQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.URL.S3URL
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.config.mapping.SearchParams.{LexicalParams, SemanticInferenceParams}
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, Language, SearchParams}
import ai.nixiesearch.config.{Config, InferenceConfig}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IdField, TextField}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.main.CliConfig.CliArgs
import ai.nixiesearch.main.CliConfig.CliArgs.TraceArgs
import ai.nixiesearch.main.subcommands.StandaloneMode.Nixiesearch
import ai.nixiesearch.util.{EnvVars, S3Client}
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream

import scala.util.Random

object TraceMode extends Mode[TraceArgs] {
  val config = Config(
    inference = InferenceConfig(
      embedding = Map(
        ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
          model = HuggingFaceHandle("onnx-community", "Qwen3-Embedding-0.6B-ONNX"),
          file = Some("onnx/model_uint8.onnx")
        )
      )
    ),
    schema = Map(
      IndexName("test") -> IndexMapping(
        name = IndexName("test"),
        fields = List(
          TextFieldSchema(
            name = StringName("title"),
            search = SearchParams(
              lexical = Some(LexicalParams(analyze = English)),
              semantic = Some(
                SemanticInferenceParams(
                  model = ModelRef("text")
                )
              )
            )
          )
        ),
        store = LocalStoreConfig()
      )
    )
  )

  def randomTitle() = {
    val size = Random.nextInt(10)
    (0 until size).map(i => s"t$i").mkString(" ")
  }

  def makeDocs(): List[Document] =
    (0 until 1000).map(i => Document(IdField("_id", i.toString), TextField("title", randomTitle()))).toList

  override def run(args: CliArgs.TraceArgs, env: EnvVars): IO[Unit] = {
    init(args, env).use(nixie => trace(nixie))
  }

  def init(args: TraceArgs, env: EnvVars): Resource[IO, Nixiesearch] = for {
    nixie  <- StandaloneMode.api(config)
    env    <- Resource.eval(EnvVars.load())
    client <- Resource.eval(Config.load(S3URL("nixiesearch-lambda-wiki", "config.yml"), env))
  } yield {
    nixie
  }

  def trace(nixie: Nixiesearch): IO[Unit] = for {
    docs <- IO(makeDocs())
    index    = nixie.indexes.head
    indexer  = nixie.indexers.head
    searcher = nixie.searchers.head
    _        <- indexer.addDocuments(docs)
    _        <- indexer.flush()
    _        <- index.sync()
    _        <- searcher.sync()
    response <- searcher.search(
      SearchRequest(
        query = SemanticQuery(
          field = "title",
          query = "yo"
        )
      )
    )
    _ <- IO.print(s"found ${response.hits.size} docs")
  } yield {}
}
