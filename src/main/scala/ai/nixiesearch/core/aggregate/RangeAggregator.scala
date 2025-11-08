package ai.nixiesearch.core.aggregate

import ai.nixiesearch.api.aggregation.Aggregation.{AggRange, RangeAggregation}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{
  DateFieldSchema,
  DateTimeFieldSchema,
  DoubleFieldSchema,
  DoubleListFieldSchema,
  FloatFieldSchema,
  FloatListFieldSchema,
  IntFieldSchema,
  IntListFieldSchema,
  LongFieldSchema,
  LongListFieldSchema
}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.{Field, Logging}
import ai.nixiesearch.core.aggregate.AggregationResult.{RangeAggregationResult, RangeCount}
import ai.nixiesearch.core.field.DateTimeFieldCodec.DateTime
import cats.effect.IO
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.facet.range.{DoubleRange, DoubleRangeFacetCounts, LongRange, LongRangeFacetCounts}
import org.apache.lucene.index.IndexReader

object RangeAggregator extends Logging {
  def aggregate(
      reader: IndexReader,
      request: RangeAggregation,
      facets: FacetsCollector,
      field: FieldSchema[? <: Field]
  ): IO[RangeAggregationResult] = field match {
    case _: IntFieldSchema | _: LongFieldSchema | _: IntListFieldSchema | _: LongListFieldSchema =>
      intLongAggregate(reader, request, facets)
    case _: DateFieldSchema | _: DateTimeFieldSchema => intLongAggregate(reader, request, facets)
    case _: FloatFieldSchema | _: DoubleFieldSchema | _: FloatListFieldSchema | _: DoubleListFieldSchema =>
      doubleFloatAggregate(reader, request, facets)
    case other => IO.raiseError(UserError(s"cannot do range aggregation for a non-numeric field ${field.name}"))
  }

  def intLongAggregate(
      reader: IndexReader,
      request: RangeAggregation,
      facets: FacetsCollector
  ): IO[RangeAggregationResult] = IO {
    val ranges = request.ranges.map {
      case AggRange(None, None) =>
        logger.warn(s"Received range aggregation $request with no from/to values - this should not occur.")
        new LongRange(s"*-*", Long.MinValue, false, Long.MaxValue, false)
      case AggRange(Some(from), None) =>
        new LongRange(s"${from.value.toLong}-*", from.value.toLong, from.inclusive, Long.MaxValue, false)
      case AggRange(None, Some(to)) =>
        new LongRange(s"*-${to.value.toLong}", Long.MinValue, true, to.value.toLong, to.inclusive)
      case AggRange(Some(from), Some(to)) =>
        new LongRange(
          s"${from.value.toLong}-${to.value.toLong}",
          from.value.toLong,
          from.inclusive,
          to.value.toLong,
          to.inclusive
        )
    }
    val counts  = new LongRangeFacetCounts(request.field, facets, ranges*)
    val buckets = for {
      (count, range) <- counts.getAllChildren(request.field).labelValues.map(_.value.intValue()).zip(request.ranges)
    } yield {
      RangeCount(range.from, range.to, count)
    }
    RangeAggregationResult(buckets.toList)
  }

  def doubleFloatAggregate(
      reader: IndexReader,
      request: RangeAggregation,
      facets: FacetsCollector
  ): IO[RangeAggregationResult] = IO {
    val ranges = request.ranges.map {
      case AggRange(None, None) =>
        logger.warn(
          s"Received range aggregation $request with no from/to values - this should not occur (parser should reject this)."
        )
        new DoubleRange(s"*-*", Double.MinValue, true, Double.MaxValue, false)
      case AggRange(Some(from), None) =>
        new DoubleRange(s"${from.value.toDouble}-*", from.value.toDouble, from.inclusive, Double.MaxValue, false)
      case AggRange(None, Some(to)) =>
        new DoubleRange(s"*-${to.value.toDouble}", Double.MinValue, true, to.value.toDouble, to.inclusive)
      case AggRange(Some(from), Some(to)) =>
        new DoubleRange(
          s"${from.value.toDouble}-${to.value.toDouble}",
          from.value.toDouble,
          from.inclusive,
          to.value.toDouble,
          to.inclusive
        )
    }
    val counts  = new DoubleRangeFacetCounts(request.field, facets, ranges*)
    val buckets = for {
      (count, range) <- counts.getAllChildren(request.field).labelValues.map(_.value.intValue()).zip(request.ranges)
    } yield {
      RangeCount(range.from, range.to, count)
    }
    RangeAggregationResult(buckets.toList)
  }
}
