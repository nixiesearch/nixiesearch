package ai.nixiesearch.util

import ai.nixiesearch.api.IndexModifyRoute.IndexResponse
import org.scalactic.Equality

object IndexResponseEquality {
  implicit val indexResponseEquality: Equality[IndexResponse] = new Equality[IndexResponse] {
    override def areEqual(a: IndexResponse, b: Any): Boolean = b match {
      case c: IndexResponse => a.status == c.status
      case _                => false
    }
  }
}
