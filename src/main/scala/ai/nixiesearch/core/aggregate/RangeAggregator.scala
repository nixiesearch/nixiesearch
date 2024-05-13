package ai.nixiesearch.core.aggregate

import ai.nixiesearch.api.aggregation.Aggregation.{AggRange, RangeAggregation, TermAggregation}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{DoubleFieldSchema, FloatFieldSchema, IntFieldSchema, LongFieldSchema}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.aggregate.AggregationResult.{RangeAggregationResult, RangeCount}
import cats.effect.IO
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.facet.range.{DoubleRange, DoubleRangeFacetCounts, LongRange, LongRangeFacetCounts}
import org.apache.lucene.index.IndexReader

object RangeAggregator {
  def aggregate(
      reader: IndexReader,
      request: RangeAggregation,
      facets: FacetsCollector,
      field: FieldSchema[? <: Field]
  ): IO[RangeAggregationResult] = field match {
    case int: IntFieldSchema    => intLongAggregate(reader, request, facets)
    case long: LongFieldSchema  => intLongAggregate(reader, request, facets)
    case fl: FloatFieldSchema   => doubleFloatAggregate(reader, request, facets)
    case dbl: DoubleFieldSchema => doubleFloatAggregate(reader, request, facets)
    case other => IO.raiseError(UserError(s"cannot do range aggregation for a non-numeric field ${field.name}"))
  }

  def intLongAggregate(
      reader: IndexReader,
      request: RangeAggregation,
      facets: FacetsCollector
  ): IO[RangeAggregationResult] = IO {
    val ranges = request.ranges.map {
      case AggRange.RangeFrom(from) =>
        new LongRange(s"$from-*", math.round(from.value), from.inclusive, Long.MaxValue, false)
      case AggRange.RangeTo(to) => new LongRange(s"*-$to", Long.MinValue, true, math.round(to.value), to.inclusive)
      case AggRange.RangeFromTo(from, to) =>
        new LongRange(s"$from-$to", math.round(from.value), from.inclusive, math.round(to.value), to.inclusive)
    }
    val counts = new LongRangeFacetCounts(request.field, facets, ranges*)
    val buckets = for {
      (count, range) <- counts.getAllChildren(request.field).labelValues.map(_.value.intValue()).zip(request.ranges)
    } yield {
      range match {
        case AggRange.RangeFrom(from)       => RangeCount(Some(from), None, count)
        case AggRange.RangeTo(to)           => RangeCount(None, Some(to), count)
        case AggRange.RangeFromTo(from, to) => RangeCount(Some(from), Some(to), count)
      }
    }
    RangeAggregationResult(buckets.toList)
  }

  def doubleFloatAggregate(
      reader: IndexReader,
      request: RangeAggregation,
      facets: FacetsCollector
  ): IO[RangeAggregationResult] = IO {
    val ranges = request.ranges.map {
      case AggRange.RangeFrom(from) => new DoubleRange(s"$from-*", from.value, from.inclusive, Double.MaxValue, false)
      case AggRange.RangeTo(to)     => new DoubleRange(s"*-$to", Double.MinValue, true, to.value, to.inclusive)
      case AggRange.RangeFromTo(from, to) =>
        new DoubleRange(s"$from-$to", from.value, from.inclusive, to.value, to.inclusive)
    }
    val counts = new DoubleRangeFacetCounts(request.field, facets, ranges*)
    val buckets = for {
      (count, range) <- counts.getAllChildren(request.field).labelValues.map(_.value.intValue()).zip(request.ranges)
    } yield {
      range match {
        case AggRange.RangeFrom(from)       => RangeCount(Some(from), None, count)
        case AggRange.RangeTo(to)           => RangeCount(None, Some(to), count)
        case AggRange.RangeFromTo(from, to) => RangeCount(Some(from), Some(to), count)
      }
    }
    RangeAggregationResult(buckets.toList)
  }
}
