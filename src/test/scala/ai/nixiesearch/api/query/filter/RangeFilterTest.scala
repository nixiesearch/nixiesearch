package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.RangePredicate
import ai.nixiesearch.api.query.filter.RangeFilterTest.RangeFilterTestForType
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.{Document, Field}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.FiniteRange.Higher.{Lt, Lte}
import ai.nixiesearch.core.FiniteRange.Lower.{Gt, Gte}
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName

class IntRangeFilterTest extends RangeFilterTestForType[IntField, IntFieldSchema] {
  override def schema()          = IntFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = IntField("field", value)
}

class IntListRangeFilterTest extends RangeFilterTestForType[IntListField, IntListFieldSchema] {
  override def schema()          = IntListFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = IntListField("field", List(value))
}

class LongRangeFilterTest extends RangeFilterTestForType[LongField, LongFieldSchema] {
  override def schema()          = LongFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = LongField("field", value)
}

class LongListRangeFilterTest extends RangeFilterTestForType[LongListField, LongListFieldSchema] {
  override def schema()          = LongListFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = LongListField("field", List(value))
}

class FloatRangeFilterTest extends RangeFilterTestForType[FloatField, FloatFieldSchema] {
  override def schema()          = FloatFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = FloatField("field", value.toFloat)
}

class FloatListRangeFilterTest extends RangeFilterTestForType[FloatListField, FloatListFieldSchema] {
  override def schema()          = FloatListFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = FloatListField("field", List(value.toFloat))
}

class DoubleRangeFilterTest extends RangeFilterTestForType[DoubleField, DoubleFieldSchema] {
  override def schema()          = DoubleFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = DoubleField("field", value.toDouble)
}

class DoubleListRangeFilterTest extends RangeFilterTestForType[DoubleListField, DoubleListFieldSchema] {
  override def schema()          = DoubleListFieldSchema(StringName("field"), filter = true)
  override def field(value: Int) = DoubleListField("field", List(value.toDouble))
}

class DateRangeFilterTest extends RangeFilterTestForType[DateField, DateFieldSchema] {
  override def schema(): DateFieldSchema    = DateFieldSchema(StringName("field"), filter = true)
  override def field(value: Int): DateField = DateField("field", value)
}

class DateTimeRangeFilterTest extends RangeFilterTestForType[DateTimeField, DateTimeFieldSchema] {
  override def schema(): DateTimeFieldSchema    = DateTimeFieldSchema(StringName("field"), filter = true)
  override def field(value: Int): DateTimeField = DateTimeField("field", value)
}

object RangeFilterTest {

  trait RangeFilterTestForType[F <: Field, S <: FieldSchema[F]] extends SearchTest with Matchers {
    def schema(): S
    def field(value: Int): F

    val mapping = IndexMapping(
      name = IndexName.unsafe("test"),
      fields = List(
        IdFieldSchema(StringName("_id")),
        schema()
      ),
      store = LocalStoreConfig(MemoryLocation())
    )
    val docs = List(
      Document(List(IdField("_id", "1"), field(10))),
      Document(List(IdField("_id", "2"), field(15))),
      Document(List(IdField("_id", "3"), field(20))),
      Document(List(IdField("_id", "4"), field(30)))
    )

    it should "select all on wide range" in withIndex { index =>
      {
        val result = index.search(filters = Some(Filters(include = Some(RangePredicate("field", Gte(0), Lte(100))))))
        result shouldBe List("1", "2", "3", "4")
      }
    }

    it should "select none on out-of-range" in withIndex { index =>
      {
        val result = index.search(filters = Some(Filters(include = Some(RangePredicate("field", Gte(100), Lte(200))))))
        result shouldBe Nil
      }
    }

    it should "select subset" in withIndex { index =>
      {
        val result = index.search(filters = Some(Filters(include = Some(RangePredicate("field", Gte(12), Lte(22))))))
        result shouldBe List("2", "3")
      }
    }

    it should "include the borders" in withIndex { index =>
      {
        val result = index.search(filters = Some(Filters(include = Some(RangePredicate("field", Gte(10), Lte(20))))))
        result shouldBe List("1", "2", "3")
      }
    }

    it should "exclude the borders" in withIndex { index =>
      {
        val result = index.search(filters = Some(Filters(include = Some(RangePredicate("field", Gt(10), Lt(20))))))
        result shouldBe List("2")
      }
    }
  }
}
