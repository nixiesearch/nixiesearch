package ai.nixiesearch.util

import com.google.common.collect.Maps
import com.hubspot.jinjava.Jinjava
import com.hubspot.jinjava.interpret.DynamicVariableResolver

object JinjavaTest {
  def main(args: Array[String]): Unit = {
    val jj = new Jinjava()
    val resolv = new DynamicVariableResolver {
      override def apply(t: String): AnyRef = {
        println(t)
        null
      }
    }
    jj.getGlobalContext.setDynamicVariableResolver(resolv)
    val parser = jj.newInterpreter()
    val tpl    = "Hello {{ name }} {{ dic['second'] }}!"
    val tree   = jj.render(tpl, Maps.newHashMap())
    val br     = 1
  }
}
