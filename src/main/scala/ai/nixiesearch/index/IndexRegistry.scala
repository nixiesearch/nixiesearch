package ai.nixiesearch.index

import ai.nixiesearch.config.{FieldSchema, StoreConfig}
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.local.LocalIndex
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import cats.effect.std.{MapRef, Queue}
import cats.implicits.*
import org.apache.lucene.index.DirectoryReader

case class IndexRegistry(
    config: StoreConfig,
    indices: Ref[IO, Map[String, Index]],
    encoders: BiEncoderCache
) extends Logging {
  def updateMapping(mapping: IndexMapping): IO[Unit] = for {
    indexOption <- indices.get.map(_.get(mapping.name))
    _ <- indexOption match {
      case Some(i: LocalIndex) => i.mappingRef.set(mapping)
      case Some(other)         => IO.raiseError(new Exception("wtf"))
      case None =>
        for {
          _     <- info("creating new index")
          index <- Index.fromConfig(config, mapping, encoders)
          _     <- indices.update(_.updated(index.name, index))
        } yield {}
    }
  } yield {}

  def mapping(indexName: String): IO[Option[IndexMapping]] = for {
    indexOption <- indices.get.map(_.get(indexName))
    mappingOption <- indexOption match {
      case Some(index) => index.mappingRef.get.map(Option.apply)
      case None        => IO.none
    }
  } yield {
    mappingOption
  }

  def index(indexName: String): IO[Option[Index]] = indices.get.map(_.get(indexName))

  def close(): IO[Unit] = for {
    indexList <- indices.get.map(_.values.toList)
    _ <- info(
      s"closing index registry with ${indexList.size} active indices"
    )
    _ <- indexList.traverse(_.close())
  } yield {}
}

object IndexRegistry extends Logging {
  def create(config: StoreConfig, indices: List[IndexMapping]): Resource[IO, IndexRegistry] = for {
    encoders <- BiEncoderCache.create()
    indicesRefMap <- Resource.eval(
      Ref.ofEffect[IO, Map[String, Index]](
        indices
          .traverse(m => Index.fromConfig(config, m, encoders))
          .map(x => x.map(index => index.name -> index).toMap)
      )
    )
    _ <- Resource.eval(info(s"Index registry initialized: ${indices.size} indices, config: $config"))
    modelsToPreload <- Resource.eval(IO(indices.flatMap(_.fields.values.collect {
      case TextLikeFieldSchema(_, SemanticSearchLikeType(model, _), _, _, _, _) => model
    })))
    _ <- Resource.eval(modelsToPreload.traverse(handle => encoders.get(handle)).void)
  } yield {
    IndexRegistry(
      config = config,
      indices = indicesRefMap,
      encoders = encoders
    )
  }
}
