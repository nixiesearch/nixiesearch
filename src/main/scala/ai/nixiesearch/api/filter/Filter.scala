package ai.nixiesearch.api.filter

import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, ConstantScoreQuery, TermQuery, Query as LuceneQuery}

case class Filter(include: Option[Predicate] = None, exclude: Option[Predicate] = None) {
  def compile(mapping: IndexMapping): IO[Option[LuceneQuery]] = {
    if (include.isEmpty) {
      exclude match {
        case Some(value) => value.compile(mapping).map(Option.apply)
        case None        => IO.none
      }
    } else if (exclude.isEmpty) {
      include match {
        case Some(value) => value.compile(mapping).map(Option.apply)
        case None        => IO.none
      }
    } else {
      for {
        builder <- IO.pure(new BooleanQuery.Builder())
        _ <- include match {
          case Some(pred) => pred.compile(mapping).map(q => builder.add(new BooleanClause(q, Occur.FILTER)))
          case None       => IO.unit
        }
        _ <- exclude match {
          case Some(pred) => pred.compile(mapping).map(q => builder.add(new BooleanClause(q, Occur.MUST_NOT)))
          case None       => IO.unit
        }
      } yield {
        Some(builder.build())
      }
    }
  }

}

object Filter {
  implicit val filterEncoder: Encoder[Filter] = deriveEncoder
  implicit val filterDecoder: Decoder[Filter] = deriveDecoder
}
