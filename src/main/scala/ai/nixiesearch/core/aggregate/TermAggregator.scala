package ai.nixiesearch.core.aggregate

import ai.nixiesearch.api.aggregation.Aggregation.{TermAggSize, TermAggregation}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.aggregate.AggregationResult.{TermAggregationResult, TermCount}
import ai.nixiesearch.core.Field.{DateField, DateTimeField}
import ai.nixiesearch.core.field.{DateFieldCodec, DateTimeFieldCodec}
import cats.effect.IO
import org.apache.lucene.index.IndexReader
import org.apache.lucene.facet.{
  FacetsCollector,
  LongValueFacetCounts,
  StringDocValuesReaderState,
  StringValueFacetCounts
}

object TermAggregator {
  def aggregate(
      reader: IndexReader,
      request: TermAggregation,
      facets: FacetsCollector,
      field: FieldSchema[? <: Field]
  ): IO[TermAggregationResult] = {
    field match {
      case _: TextFieldSchema | _: TextListFieldSchema => IO(aggregateString(reader, request, facets))
      case _: IntFieldSchema | _: LongFieldSchema | _: DoubleFieldSchema | _: FloatFieldSchema | _: IntListFieldSchema |
          _: LongListFieldSchema | _: FloatListFieldSchema | _: DoubleListFieldSchema =>
        IO(aggregateLong(reader, request, facets))
      case _: DateFieldSchema =>
        IO(aggregateLong(reader, request, facets)).map(result =>
          TermAggregationResult(result.buckets.map(tc => tc.copy(term = DateFieldCodec.writeString(tc.term.toInt))))
        )
      case _: DateTimeFieldSchema =>
        IO(aggregateLong(reader, request, facets)).map(result =>
          TermAggregationResult(
            result.buckets.map(tc => tc.copy(term = DateTimeFieldCodec.writeString(tc.term.toLong)))
          )
        )
      case other => IO.raiseError(UserError(s"term aggregation does not support type $other"))
    }
  }

  val MAX_TERM_FACETS = 128 * 1024
  def aggregateString(reader: IndexReader, request: TermAggregation, facets: FacetsCollector): TermAggregationResult = {
    val state  = new StringDocValuesReaderState(reader, request.field)
    val counts = StringValueFacetCounts(state, facets)
    val size   = request.size match {
      case TermAggSize.ExactTermAggSize(value) => value
      case TermAggSize.AllTermAggSize          => MAX_TERM_FACETS
    }
    val top = counts.getTopChildren(size, request.field)
    TermAggregationResult(top.labelValues.toList.map(lv => TermCount(lv.label, lv.value.intValue())))
  }

  def aggregateLong(reader: IndexReader, request: TermAggregation, facets: FacetsCollector): TermAggregationResult = {
    val counts = new LongValueFacetCounts(request.field, facets)
    val size   = request.size match {
      case TermAggSize.ExactTermAggSize(value) => value
      case TermAggSize.AllTermAggSize          => MAX_TERM_FACETS
    }
    val top = counts.getTopChildren(size, request.field)
    TermAggregationResult(top.labelValues.toList.map(lv => TermCount(lv.label, lv.value.intValue())))
  }

}
