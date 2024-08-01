package ai.nixiesearch.api.ui

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.query.{MatchQuery, MultiMatchQuery}
import ai.nixiesearch.api.ui.WebuiTemplate.WebuiDoc
import ai.nixiesearch.core.Field.{FloatField, TextField}
import ai.nixiesearch.core.{Document, Field}
import com.hubspot.jinjava.Jinjava
import cats.effect.IO
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import java.util.Map as JMap
import java.util.List as JList
import java.lang.Double as JDouble

case class WebuiTemplate(jinja: Jinjava, template: String) {
  def empty(indexes: List[String], suggests: List[String]): IO[String] = IO {
    val ctx = JMap.of(
      "query",
      "",
      "docs",
      JList.of[String]()
    )
    jinja.render(template, ctx)
  }
  def render(
      index: String,
      request: SearchRequest,
      response: SearchResponse
  ): IO[String] = IO {
    val query = request.query match {
      case MatchQuery(_, query, _)      => query
      case MultiMatchQuery(query, _, _) => query
      case _                            => ""
    }
    val docs = response.hits match {
      case Nil => List[JMap[String, String]]().asJava
      case ne  => ne.map(doc => WebuiDoc(doc).asJavaMap()).asJava
    }
    val ctx = JMap.of(
      "index",
      index,
      "suggests",
      List[String]().asJava,
      "query",
      query,
      "docs",
      docs
    )
    jinja.render(template, ctx)
  }
}

object WebuiTemplate {
  case class WebuiDoc(
      id: String,
      score: Double,
      fields: JMap[String, String],
      tags: JMap[String, JList[String]],
      image: String
  ) {
    def asJavaMap(): JMap[String, Object] = {
      JMap.of[String, Object](
        "_id",
        id,
        "_score",
        JDouble.valueOf(score),
        "fields",
        fields,
        "tags",
        tags,
        "_image",
        image
      )
    }
  }
  object WebuiDoc {
    val imagePatterns                     = List("img", "image")
    val reservedFields                    = Set("_id", "_score")
    def maybeImage(name: String): Boolean = imagePatterns.exists(pat => name.contains(pat))
    def isReserved(name: String): Boolean = reservedFields.contains(name) || maybeImage(name)

    def apply(doc: Document): WebuiDoc = {
      val id    = doc.fields.collectFirst { case TextField("_id", value) => value }.getOrElse("<none>")
      val score = doc.fields.collectFirst { case FloatField("_score", value) => value }.getOrElse(0.0f)
      val image = doc.fields
        .collectFirst { case TextField(name, value) if maybeImage(name) => value }
        .getOrElse("_ui/assets/noimg.png")
      val fields = doc.fields.filterNot(f => isReserved(f.name)).collect {
        case Field.TextField(name, value)   => name -> value
        case Field.IntField(name, value)    => name -> value.toString
        case Field.LongField(name, value)   => name -> value.toString
        case Field.FloatField(name, value)  => name -> value.toString
        case Field.DoubleField(name, value) => name -> value.toString
      }
      val tags = doc.fields.filterNot(f => isReserved(f.name)).collect { case Field.TextListField(name, values) =>
        name -> values.asJava
      }
      WebuiDoc(id = id, score = score, image = image, fields = fields.toMap.asJava, tags = tags.toMap.asJava)
    }
  }

  def apply() = {
    val template = IOUtils.resourceToString("/ui/search.jinja2.html", StandardCharsets.UTF_8)
    val jinja    = new Jinjava()
    new WebuiTemplate(jinja, template)
  }

}
