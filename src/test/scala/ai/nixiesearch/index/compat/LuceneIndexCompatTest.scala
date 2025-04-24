package ai.nixiesearch.index.compat

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SuggestRequest}
import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.{BoolPredicate, TermPredicate}
import ai.nixiesearch.api.filter.Predicate.BoolPredicate.AndPredicate
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.index.Searcher
import cats.effect.IO
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import scala.language.implicitConversions
import scala.compiletime.uninitialized

class Lucene912IndexCompatTest extends LuceneIndexCompatTest("lucene9.12")

class Lucene101IndexCompatTest extends LuceneIndexCompatTest("lucene10.1")

abstract class LuceneIndexCompatTest(name: String) extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  lazy val pwd           = System.getProperty("user.dir")
  var searcher: Searcher = uninitialized
  var shutdown: IO[Unit] = uninitialized

  override def beforeAll(): Unit = {
    val (s, close) = CompatUtil.readResource(s"$pwd/src/test/resources/compat/$name").allocated.unsafeRunSync()
    searcher = s
    shutdown = close
  }

  override def afterAll(): Unit = {
    shutdown.unsafeRunSync()
  }

  it should "fetch all fields" in {
    val response = searcher
      .search(SearchRequest(query = MatchAllQuery(), fields = CompatUtil.mapping.fields.keys.toList))
      .unsafeRunSync()
    response.hits.map(_.fields.size) shouldBe List(10)
  }

  it should "filter over numbers" in {
    val response = searcher
      .search(
        SearchRequest(
          query = MatchAllQuery(),
          filters = Some(
            Filters(include =
              Some(
                AndPredicate(
                  List(
                    TermPredicate("int", 1),
                    TermPredicate("long", 1)
                  )
                )
              )
            )
          )
        )
      )
      .unsafeRunSync()
    response.hits.size shouldBe 1
  }

  it should "filter over strings" in {
    val response = searcher
      .search(
        SearchRequest(
          query = MatchAllQuery(),
          filters = Some(
            Filters(include =
              Some(
                AndPredicate(
                  List(
                    TermPredicate("text_lexical", "test"),
                    TermPredicate("text_array", "test")
                  )
                )
              )
            )
          )
        )
      )
      .unsafeRunSync()
    response.hits.size shouldBe 1
  }

  it should "facet over scalars" in {
    val response = searcher
      .search(
        SearchRequest(
          query = MatchAllQuery(),
          aggs = Some(
            Aggs(
              Map(
                "int"          -> TermAggregation("int", 10),
                "float"        -> TermAggregation("float", 10),
                "long"         -> TermAggregation("long", 10),
                "double"       -> TermAggregation("double", 10),
                "date"         -> TermAggregation("date", 10),
                "datetime"     -> TermAggregation("datetime", 10),
                "text_lexical" -> TermAggregation("text_lexical", 10),
                "text_array"   -> TermAggregation("text_array", 10)
              )
            )
          )
        )
      )
      .unsafeRunSync()
    response.aggs.size shouldBe 8
  }

  it should "run suggests over singular text fields" in {
    val response = searcher.suggest(SuggestRequest("t", fields = List("text_lexical"))).unsafeRunSync()
    response.suggestions.size shouldBe 1
  }

  it should "run suggests over array text fields" in {
    val response = searcher.suggest(SuggestRequest("t", fields = List("text_array"))).unsafeRunSync()
    response.suggestions.size shouldBe 1
  }
}
