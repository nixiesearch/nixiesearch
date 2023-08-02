import sbt.*

object Deps {
  lazy val http4sVersion    = "1.0.0-M38"
  lazy val scalatestVersion = "3.2.16"
  lazy val circeVersion     = "0.14.5"
  lazy val circeYamlVersion = "0.14.2"
  lazy val fs2Version       = "3.2.2"
  lazy val luceneVersion    = "9.7.0"
  lazy val awsVersion       = "2.20.48"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
