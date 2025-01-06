package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.RangePredicate
import ai.nixiesearch.api.query.filter.RangeFilterTest.RangeFilterTestForType
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{
  DateFieldSchema,
  DateTimeFieldSchema,
  DoubleFieldSchema,
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema
}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.{Document, Field}
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.FiniteRange.Higher.{Lt, Lte}
import ai.nixiesearch.core.FiniteRange.Lower.{Gt, Gte}
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IntRangeFilterTest extends RangeFilterTestForType[IntField, IntFieldSchema] {
  override def schema()          = IntFieldSchema("field", filter = true)
  override def field(value: Int) = IntField("field", value)
}

class LongRangeFilterTest extends RangeFilterTestForType[LongField, LongFieldSchema] {
  override def schema()          = LongFieldSchema("field", filter = true)
  override def field(value: Int) = LongField("field", value)
}

class FloatRangeFilterTest extends RangeFilterTestForType[FloatField, FloatFieldSchema] {
  override def schema()          = FloatFieldSchema("field", filter = true)
  override def field(value: Int) = FloatField("field", value.toFloat)
}

class DoubleRangeFilterTest extends RangeFilterTestForType[DoubleField, DoubleFieldSchema] {
  override def schema()          = DoubleFieldSchema("field", filter = true)
  override def field(value: Int) = DoubleField("field", value.toFloat)
}

class DateRangeFilterTest extends RangeFilterTestForType[DateField, DateFieldSchema] {
  override def schema(): DateFieldSchema    = DateFieldSchema("field", filter = true)
  override def field(value: Int): DateField = DateField("field", value)
}

class DateTimeRangeFilterTest extends RangeFilterTestForType[DateTimeField, DateTimeFieldSchema] {
  override def schema(): DateTimeFieldSchema    = DateTimeFieldSchema("field", filter = true)
  override def field(value: Int): DateTimeField = DateTimeField("field", value)
}

object RangeFilterTest {

  trait RangeFilterTestForType[F <: Field, S <: FieldSchema[F]] extends SearchTest with Matchers {
    def schema(): S
    def field(value: Int): F

    val mapping = IndexMapping(
      name = IndexName.unsafe("test"),
      fields = List(
        TextFieldSchema("_id", filter = true),
        schema()
      ),
      store = LocalStoreConfig(MemoryLocation())
    )
    val docs = List(
      Document(List(TextField("_id", "1"), field(10))),
      Document(List(TextField("_id", "2"), field(15))),
      Document(List(TextField("_id", "3"), field(20))),
      Document(List(TextField("_id", "4"), field(30)))
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
