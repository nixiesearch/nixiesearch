package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.aggregator.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.IndexReader
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.search.{IndexSearcher, TopDocs, Query as LuceneQuery}
import cats.implicits.*

trait Query extends Logging {
  def search(request: SearchRequest, reader: IndexReader): IO[SearchResponse]

  def aggregate(
      mapping: IndexMapping,
      reader: IndexReader,
      collector: FacetsCollector,
      aggs: Aggs
  ): IO[Map[String, AggregationResult]] = aggs.aggs.toList
    .traverse { case (name, agg) =>
      mapping.fields.get(agg.field) match {
        case Some(field) if !field.facet =>
          IO.raiseError(new Exception(s"cannot aggregate over a field marked as a non-facetable"))
        case None => IO.raiseError(new Exception(s"cannot aggregate over a field not defined in schema"))
        case Some(schema) =>
          agg match {
            case a @ Aggregation.TermAggregation(field, size) =>
              TermAggregator.aggregate(reader.reader, a, collector, schema).map(result => name -> result)
            case a @ Aggregation.RangeAggregation(field, ranges) =>
              RangeAggregator.aggregate(reader.reader, a, collector, schema).map(result => name -> result)
          }
      }
    }
    .map(_.toMap)

  protected def collect(
      mapping: IndexMapping,
      reader: IndexReader,
      top: TopDocs,
      fields: List[String]
  ): IO[List[Document]] = IO {
    val fieldSet = fields.toSet
    val docs = top.scoreDocs.map(doc => {
      val visitor = DocumentVisitor(mapping, fieldSet)
      reader.reader.storedFields().document(doc.doc, visitor)
      visitor.asDocument()
    })
    docs.toList
  }

}

object Query extends Logging {

  implicit val queryEncoder: Encoder[Query] = Encoder.instance {
    case q: MatchAllQuery   => withType("match_all", MatchAllQuery.matchAllQueryEncoder(q))
    case q: MatchQuery      => withType("match", MatchQuery.matchQueryEncoder(q))
    case q: MultiMatchQuery => withType("multi_match", MultiMatchQuery.multiMatchQueryEncoder(q))
  }

  def withType(tpe: String, value: Json) = Json.fromJsonObject(JsonObject.fromIterable(List(tpe -> value)))

  implicit val queryDecoder: Decoder[Query] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.toList.headOption match {
          case Some(("multi_match", json)) => MultiMatchQuery.multiMatchQueryDecoder.decodeJson(json)
          case Some(("match_all", json))   => MatchAllQuery.matchAllQueryDecoder.decodeJson(json)
          case Some(("match", json))       => MatchQuery.matchQueryDecoder.decodeJson(json)
          case Some((other, _))            => Left(DecodingFailure(s"query type '$other' not supported", c.history))
          case None => Left(DecodingFailure(s"expected non-empty json object, but got $obj", c.history))
        }
      case None => Left(DecodingFailure("expected json object", c.history))
    }
  )

}
