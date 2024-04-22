package ai.nixiesearch.main

import org.apache.commons.lang3.reflect.FieldUtils
import org.apache.lucene.search.suggest.fst.WFSTCompletionLookup
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.fst.{FST, FSTCompiler}

import java.nio.file.Paths

object WFSTTest {
  def main(args: Array[String]): Unit = {
    val dir   = new MMapDirectory(Paths.get("/tmp"))
    val sug   = new WFSTCompletionLookup(dir, "wfst")
    val field = FieldUtils.getField(classOf[FSTCompiler.Builder[_]], "suffixRAMLimitMB", true)
    val b     = 1
  }
}
