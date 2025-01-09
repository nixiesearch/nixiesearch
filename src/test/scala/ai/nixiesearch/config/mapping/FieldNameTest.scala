package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldNameTest extends AnyFlatSpec with Matchers {
  it should "parse simple fields" in {
    FieldName.parse("title") shouldBe Right(StringName("title"))
  }

  it should "parse wildcard fields" in {
    FieldName.parse("field_*_str") shouldBe Right(WildcardName("field_*_str", "field_", "_str"))
  }

  it should "parse wildcard fields at end" in {
    FieldName.parse("str_*") shouldBe Right(WildcardName("str_*", "str_", ""))
  }

  it should "parse wildcard fields at start" in {
    FieldName.parse("*_str") shouldBe Right(WildcardName("*_str", "", "_str"))
  }

  it should "fail on too many placeholders" in {
    FieldName.parse("field_*_str_*") shouldBe a[Left[?, ?]]
  }
}
