package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.FieldName.{NestedName, NestedWildcardName, StringName, WildcardName}
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

  it should "parse nested fields" in {
    FieldName.parse("root.child") shouldBe Right(NestedName("root.child", "root", "child"))
  }

  it should "parse nested wildcard fields" in {
    FieldName.parse("root.field_*_str") shouldBe Right(
      NestedWildcardName("root.field_*_str", "root", "field_*_str", "root.field_", "_str")
    )
  }

  it should "parse nested wildcard fields with wildcard at end" in {
    FieldName.parse("parent.str_*") shouldBe Right(
      NestedWildcardName("parent.str_*", "parent", "str_*", "parent.str_", "")
    )
  }

  it should "parse nested wildcard fields with wildcard at start of child" in {
    FieldName.parse("parent.*_str") shouldBe Right(
      NestedWildcardName("parent.*_str", "parent", "*_str", "parent.", "_str")
    )
  }

  it should "parse nested wildcard with underscore separator" in {
    FieldName.parse("meta.field_*_int") shouldBe Right(
      NestedWildcardName("meta.field_*_int", "meta", "field_*_int", "meta.field_", "_int")
    )
  }

  it should "fail on multiple dots" in {
    FieldName.parse("root.child.grandchild") shouldBe a[Left[?, ?]]
  }

  it should "fail on multiple wildcards in nested field" in {
    FieldName.parse("root.field_*_str_*") shouldBe a[Left[?, ?]]
  }

  it should "fail on multiple dots in nested wildcard" in {
    FieldName.parse("root.child.field_*_str") shouldBe a[Left[?, ?]]
  }

  it should "fail on wildcard before dot" in {
    FieldName.parse("field_*.child") shouldBe a[Left[?, ?]]
  }
}
