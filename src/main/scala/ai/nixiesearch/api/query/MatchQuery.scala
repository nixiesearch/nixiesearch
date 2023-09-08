package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.api.query.MatchQuery.Operator.OR
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.{IndexMapping, Language}
import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, ModelPrefix, SemanticSearch}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.IndexReader
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.apache.lucene.search.{
  BooleanClause,
  BooleanQuery,
  IndexSearcher,
  KnnFloatVectorQuery,
  MultiCollector,
  TermQuery,
  TopScoreDocCollector,
  Query as LuceneQuery
}
import io.circe.generic.semiauto.*
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur

import scala.util.{Failure, Success}

case class MatchQuery(field: String, query: String, operator: Operator = OR) extends Query with Logging {
  override def search(request: SearchRequest, reader: IndexReader): IO[SearchResponse] = {
    for {
      mapping <- reader.mapping()
      fieldSchema <- IO.fromOption(mapping.fields.get(field))(
        new Exception(s"cannot search over a non-existent field $field")
      )
      result <- fieldSchema match {
        case TextLikeFieldSchema(_, SemanticSearch(model, prefix), _, _, _, _) =>
          searchSemantic(request, reader, model, prefix, mapping)
        case TextLikeFieldSchema(_, LexicalSearch(language), _, _, _, _) =>
          searchLexical(request, reader, language, mapping)
        case other => IO.raiseError(new Exception(s"cannot search over a non-text field $other"))
      }
    } yield {
      result
    }

  }

  def searchLexical(request: SearchRequest, reader: IndexReader, language: Language, mapping: IndexMapping) = for {
    start   <- IO(System.currentTimeMillis())
    terms   <- IO(language.analyze(field, query))
    builder <- IO.pure(new BooleanQuery.Builder())
    _ <- IO(
      terms.foreach(term => builder.add(new BooleanClause(new TermQuery(new Term(field, term)), operator.occur)))
    )
    filterOption <- request.filter.toLuceneQuery(mapping)
    _ <- filterOption match {
      case Some(value) => IO(builder.add(new BooleanClause(value, Occur.FILTER)))
      case None        => IO.unit
    }
    query          <- IO(builder.build())
    topCollector   <- IO.pure(TopScoreDocCollector.create(request.size, request.size))
    facetCollector <- IO.pure(new FacetsCollector(false))
    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
    _              <- IO(reader.searcher.search(query, collector))
    docs           <- collect(mapping, reader, topCollector.topDocs(), request.fields)
    aggs           <- aggregate(mapping, reader, facetCollector, request.aggs)
    end            <- IO(System.currentTimeMillis())
  } yield {
    SearchResponse(end - start, docs, aggs)
  }

  def searchSemantic(
      request: SearchRequest,
      reader: IndexReader,
      model: ModelHandle,
      prefix: ModelPrefix,
      mapping: IndexMapping
  ) = for {
    start        <- IO(System.currentTimeMillis())
    encoder      <- reader.encoders.get(model)
    queryEmbed   <- IO(encoder.embed(prefix.query + query))
    filterOption <- request.filter.toLuceneQuery(mapping)
    query <- filterOption match {
      case Some(filter) => IO(new KnnFloatVectorQuery(field, queryEmbed, request.size, filter))
      case None         => IO(new KnnFloatVectorQuery(field, queryEmbed, request.size))
    }
    topCollector   <- IO.pure(TopScoreDocCollector.create(request.size, request.size))
    facetCollector <- IO.pure(new FacetsCollector(false))
    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
    _              <- IO(reader.searcher.search(query, collector))
    docs           <- collect(mapping, reader, topCollector.topDocs(), request.fields)
    aggs           <- aggregate(mapping, reader, facetCollector, request.aggs)
    end            <- IO(System.currentTimeMillis())
  } yield {
    SearchResponse(end - start, docs, aggs)
  }
}

object MatchQuery {
  sealed trait Operator {
    def occur: BooleanClause.Occur
  }
  object Operator {
    case object AND extends Operator {
      val occur = Occur.MUST
    }
    case object OR extends Operator {
      val occur = Occur.SHOULD
    }

    implicit val operatorDecoder: Decoder[Operator] = Decoder.decodeString.emapTry {
      case "and" | "AND" => Success(AND)
      case "or" | "OR"   => Success(OR)
      case other         => Failure(new Exception(s"cannot parse operator '$other', use AND|OR"))
    }

    implicit val operatorEncoder: Encoder[Operator] = Encoder.encodeString.contramap {
      case AND => "and"
      case OR  => "or"
    }
  }

  case class FieldQuery(query: String)

  implicit val fieldQueryDecoder: Decoder[FieldQuery] = deriveDecoder
  implicit val fieldQueryEncoder: Encoder[FieldQuery] = deriveEncoder

  implicit val matchQueryEncoder: Encoder[MatchQuery] = Encoder.instance(q =>
    Json.fromJsonObject(JsonObject.fromIterable(List(q.field -> fieldQueryEncoder(FieldQuery(q.query)))))
  )
  implicit val matchQueryDecoder: Decoder[MatchQuery] = Decoder.instance(c =>
    c.as[Map[String, FieldQuery]].map(_.toList) match {
      case Left(_) =>
        c.as[Map[String, String]].map(_.toList) match {
          case Left(error)                  => Left(error)
          case Right((field, query) :: Nil) => Right(MatchQuery(field, query, OR))
          case Right(other)                 => Left(DecodingFailure(s"cannot decode query $other", c.history))
        }
      case Right((field, query) :: Nil) => Right(MatchQuery(field, query.query, OR))
      case Right(other)                 => Left(DecodingFailure(s"cannot decode query $other", c.history))
    }
  )

  def analyze(mapping: IndexMapping, field: String, query: String): IO[List[String]] = mapping.fields.get(field) match {
    case Some(TextFieldSchema(_, LexicalSearch(lang), _, _, _, _))     => IO(lang.analyze(query, field))
    case Some(TextListFieldSchema(_, LexicalSearch(lang), _, _, _, _)) => IO(lang.analyze(query, field))
    case Some(other) => IO.raiseError(new Exception(s"Cannot search over a non-text field '$field'"))
    case None        => IO.raiseError(new Exception(s"Cannot search over a non-existent field '$field'"))
  }

}
