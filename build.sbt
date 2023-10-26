import Deps.*
import sbt.Package.ManifestAttributes

version := "0.0.2"

scalaVersion := "3.3.1"

name := "nixiesearch"

libraryDependencies ++= Seq(
  "org.typelevel"            %% "cats-effect"              % "3.5.2",
  "org.scalatest"            %% "scalatest"                % scalatestVersion % "test",
  "org.scalactic"            %% "scalactic"                % scalatestVersion % "test",
  "org.scalatestplus"        %% "scalacheck-1-16"          % "3.2.14.0"       % "test",
  "ch.qos.logback"            % "logback-classic"          % "1.4.11",
  "io.circe"                 %% "circe-yaml"               % circeYamlVersion,
  "io.circe"                 %% "circe-core"               % circeVersion,
  "io.circe"                 %% "circe-generic"            % circeVersion,
  "io.circe"                 %% "circe-parser"             % circeVersion,
  "com.github.pathikrit"     %% "better-files"             % "3.9.2",
  "org.rogach"               %% "scallop"                  % "5.0.0",
  "com.github.blemale"       %% "scaffeine"                % "5.2.1",
  "org.http4s"               %% "http4s-dsl"               % http4sVersion,
  "org.http4s"               %% "http4s-blaze-server"      % http4sVersion,
  "org.http4s"               %% "http4s-blaze-client"      % http4sVersion,
  "org.http4s"               %% "http4s-circe"             % http4sVersion,
  "org.apache.lucene"         % "lucene-core"              % luceneVersion,
  "org.apache.lucene"         % "lucene-facet"             % luceneVersion,
  "org.apache.lucene"         % "lucene-queryparser"       % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-common"   % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-icu"      % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-smartcn"  % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-kuromoji" % luceneVersion,
  "org.apache.lucene"         % "lucene-analysis-stempel"  % luceneVersion,
  "commons-io"                % "commons-io"               % "2.14.0",
  "commons-codec"             % "commons-codec"            % "1.16.0",
  "org.apache.commons"        % "commons-lang3"            % "3.13.0",
  "ai.djl"                    % "api"                      % djlVersion,
  "ai.djl.huggingface"        % "tokenizers"               % djlVersion,
  "com.microsoft.onnxruntime" % "onnxruntime"              % "1.16.1",
  "com.github.luben"          % "zstd-jni"                 % "1.5.5-6"
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings",
//  "-release:20",
  "-no-indent"
)

Compile / mainClass := Some("ai.nixiesearch.main.Main")

Compile / discoveredMainClasses := Seq()

val PLATFORM = "amd64"

enablePlugins(DockerPlugin)

docker / dockerfile := {
  val artifact: File     = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from(s"--platform=$PLATFORM ubuntu:lunar-20230816")
    runRaw(
      List(
        "apt-get update",
        "apt-get install -y --no-install-recommends openjdk-20-jdk-headless htop procps curl inetutils-ping libgomp1 locales",
        "sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen",
        "rm -rf /var/lib/apt/lists/*"
      ).mkString(" && ")
    )
    runRaw(
      List(
        "mkdir -p /tmp/nixiesearch/nixiesearch/e5-small-v2-onnx/",
        "curl -L https://huggingface.co/nixiesearch/e5-small-v2-onnx/resolve/main/model.onnx -o /tmp/nixiesearch/nixiesearch/e5-small-v2-onnx/model.onnx",
        "curl -L https://huggingface.co/nixiesearch/e5-small-v2-onnx/resolve/main/config.json -o /tmp/nixiesearch/nixiesearch/e5-small-v2-onnx/config.json",
        "curl -L https://huggingface.co/nixiesearch/e5-small-v2-onnx/resolve/main/tokenizer.json -o /tmp/nixiesearch/nixiesearch/e5-small-v2-onnx/tokenizer.json"
      ).mkString(" && ")
    )
    env(
      Map(
        "LANG"     -> "en_US.UTF-8",
        "LANGUAGE" -> "en_US:en",
        "LC_ALL"   -> "en_US.UTF-8"
      )
    )
    add(new File("deploy/nixiesearch.sh"), "/nixiesearch.sh")
    add(artifact, artifactTargetPath)
    entryPoint("/nixiesearch.sh")
    cmd("--help")
  }
}

docker / imageNames := Seq(
  ImageName(s"nixiesearch/nixiesearch:${version.value}"),
  ImageName(s"nixiesearch/nixiesearch:latest")
)

docker / buildOptions := BuildOptions(
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always
)

ThisBuild / assemblyMergeStrategy := {
  case PathList("module-info.class")                                         => MergeStrategy.discard
  case "META-INF/io.netty.versions.properties"                               => MergeStrategy.first
  case "META-INF/MANIFEST.MF"                                                => MergeStrategy.discard
  case x if x.startsWith("META-INF/versions/")                               => MergeStrategy.first
  case x if x.startsWith("META-INF/services/")                               => MergeStrategy.concat
  case "META-INF/native-image/reflect-config.json"                           => MergeStrategy.concat
  case "META-INF/native-image/io.netty/netty-common/native-image.properties" => MergeStrategy.first
  case "META-INF/okio.kotlin_module"                                         => MergeStrategy.first
  case "findbugsExclude.xml"                                                 => MergeStrategy.discard
  case "log4j2-test.properties"                                              => MergeStrategy.discard
  case x if x.endsWith("/module-info.class")                                 => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

assembly / assemblyJarName          := "nixiesearch.jar"
ThisBuild / assemblyRepeatableBuild := false
packageOptions                      := Seq(ManifestAttributes(("Multi-Release", "true")))
