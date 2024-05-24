package ai.nixiesearch.main.args

import java.io.File

object FileConverter extends ArgConverter[File] {
  override def convert(value: String): Either[String, File] = {
    val file = if (value.startsWith("/")) {
      new File(value)
    } else {
      val prefix = System.getProperty("user.dir")
      new File(s"$prefix/$value")
    }
    if (file.exists()) {
      Right(file)
    } else {
      Option(file.getParentFile) match {
        case Some(parent) if parent.exists() && parent.isDirectory =>
          val other = Option(parent.listFiles()).map(_.map(_.getName).mkString("\n", "\n", "\n"))
          Left(s"$file: file does not exist. Perhaps you've meant: $other")
        case _ => Left(s"$file: file does not exist (and we cannot list parent directory)")
      }

    }

  }
}
