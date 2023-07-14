import Deps.*

version := "0.0.1-SNAPSHOT"

scalaVersion := "3.3.0"

name := "nixiesearch"

libraryDependencies ++= Seq(
  "org.typelevel"            %% "cats-effect"              % "3.4.10",
  "org.scalatest"            %% "scalatest"                % scalatestVersion % "test,it",
  "org.scalactic"            %% "scalactic"                % scalatestVersion % "test,it",
  "org.scalatestplus"        %% "scalacheck-1-16"          % "3.2.14.0"       % "test,it",
  "ch.qos.logback"            % "logback-classic"          % "1.4.7",
  "io.circe"                 %% "circe-yaml"               % circeYamlVersion,
  "io.circe"                 %% "circe-core"               % circeVersion,
  "io.circe"                 %% "circe-generic"            % circeVersion,
  "io.circe"                 %% "circe-parser"             % circeVersion,
  "com.github.pathikrit"     %% "better-files"             % "3.9.2",
  "org.rogach"               %% "scallop"                  % "4.1.0",
  "com.github.blemale"       %% "scaffeine"                % "5.2.1",
  "org.http4s"               %% "http4s-dsl"               % http4sVersion,
  "org.http4s"               %% "http4s-blaze-server"      % http4sVersion,
  "org.http4s"               %% "http4s-blaze-client"      % http4sVersion,
  "org.http4s"               %% "http4s-circe"             % http4sVersion,
  "org.apache.lucene"         % "lucene-core"              % luceneVersion,
  "org.apache.lucene"         % "lucene-facet"             % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-common"   % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-icu"      % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-smartcn"  % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-kuromoji" % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-stempel"  % luceneVersion,
  "commons-io"                % "commons-io"               % "2.11.0",
  "commons-codec"             % "commons-codec"            % "1.16.0",
  "ai.djl"                    % "api"                      % "0.22.1",
  "com.microsoft.onnxruntime" % "onnxruntime"              % "1.14.0"
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings",
  "-release:11",
  "-no-indent"
)
