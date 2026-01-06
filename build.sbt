import Deps.*
import sbt.Package.ManifestAttributes

lazy val PLATFORM      = Option(System.getenv("PLATFORM")).getOrElse("amd64")
lazy val GPU           = Option(System.getenv("GPU")).getOrElse("false").toBoolean
lazy val GRAALVM_TRACE = Option(System.getenv("GRAALVM_TRACE")).getOrElse("false").toBoolean

ThisBuild / version      := "0.8.0"
ThisBuild / scalaVersion := "3.7.4"

lazy val root = (project in file("."))
  .settings(
    name := "nixiesearch"
  )

libraryDependencies ++= Seq(
  "org.typelevel"     %% "cats-effect"     % "3.6.3",
  "org.scalatest"     %% "scalatest"       % scalatestVersion % "test",
  "org.scalactic"     %% "scalactic"       % scalatestVersion % "test",
  "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0"       % "test",
  // "ch.qos.logback"        % "logback-classic"            % "1.5.20",
  "io.circe"             %% "circe-yaml"                 % circeYamlVersion,
  "io.circe"             %% "circe-core"                 % circeVersion,
  "io.circe"             %% "circe-generic"              % circeVersion,
  "io.circe"             %% "circe-parser"               % circeVersion,
  "com.github.pathikrit" %% "better-files"               % "3.9.2",
  "org.rogach"           %% "scallop"                    % "6.0.0",
  "com.github.blemale"   %% "scaffeine"                  % "5.3.0",
  "org.http4s"           %% "http4s-dsl"                 % http4sVersion,
  "org.http4s"           %% "http4s-ember-server"        % http4sVersion,
  "org.http4s"           %% "http4s-ember-client"        % http4sVersion,
  "org.http4s"           %% "http4s-circe"               % http4sVersion,
  "org.apache.lucene"     % "lucene-core"                % luceneVersion,
  "org.apache.lucene"     % "lucene-backward-codecs"     % luceneVersion,
  "org.apache.lucene"     % "lucene-join"                % luceneVersion,
  "org.apache.lucene"     % "lucene-suggest"             % luceneVersion,
  "org.apache.lucene"     % "lucene-facet"               % luceneVersion,
  "org.apache.lucene"     % "lucene-queryparser"         % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-common"     % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-icu"        % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-nori"       % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-smartcn"    % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-kuromoji"   % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-stempel"    % luceneVersion,
  "org.apache.lucene"     % "lucene-analysis-morfologik" % luceneVersion,
  "commons-io"            % "commons-io"                 % "2.21.0",
  "commons-codec"         % "commons-codec"              % "1.20.0",
  "org.apache.commons"    % "commons-lang3"              % "3.20.0",
  "ai.djl"                % "api"                        % djlVersion,
  "ai.djl.huggingface"    % "tokenizers"                 % djlVersion,
  "com.github.luben"      % "zstd-jni"                   % "1.5.7-6",
  "com.github.blemale"   %% "scaffeine"                  % "5.3.0",
  "com.hubspot.jinjava"   % "jinjava"                    % "2.8.2" excludeAll (
    ExclusionRule(organization = "com.google.code.findbugs", name = "annotations"),
    ExclusionRule(organization = "com.google.code.findbugs", name = "jsr305")
  ),
  "software.amazon.awssdk"                 % "s3"                                     % awsVersion,
  "co.fs2"                                %% "fs2-core"                               % fs2Version,
  "co.fs2"                                %% "fs2-io"                                 % fs2Version,
  "co.fs2"                                %% "fs2-reactive-streams"                   % fs2Version,
  "org.typelevel"                         %% "log4cats-slf4j"                         % "2.7.1",
  "de.lhns"                               %% "fs2-compress-gzip"                      % fs2CompressVersion,
  "de.lhns"                               %% "fs2-compress-bzip2"                     % fs2CompressVersion,
  "de.lhns"                               %% "fs2-compress-zstd"                      % fs2CompressVersion,
  "org.apache.kafka"                       % "kafka-clients"                          % "4.1.1",
  "io.prometheus"                          % "prometheus-metrics-core"                % prometheusVersion,
  "io.prometheus"                          % "prometheus-metrics-exposition-formats"  % prometheusVersion,
  "io.prometheus"                          % "prometheus-metrics-instrumentation-jvm" % prometheusVersion,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"                    % jsoniterVersion,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe"                   % jsoniterVersion,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion % "compile-internal"
)

if (GPU) {
  libraryDependencies ++= Seq(
    "com.microsoft.onnxruntime" % "onnxruntime_gpu"      % onnxRuntimeVersion,
    "ai.nixiesearch"            % "llamacpp-server-java" % llamacppVersion classifier "cuda12-linux-x86-64"
  )
} else {
  libraryDependencies ++= Seq(
    "com.microsoft.onnxruntime" % "onnxruntime"          % onnxRuntimeVersion,
    "ai.nixiesearch"            % "llamacpp-server-java" % llamacppVersion
  )
}

libraryDependencySchemes ++= Seq(
  "com.github.luben" % "zstd-jni" % VersionScheme.Always // due to fs2-compress having exact version dep
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings",
//  "-Wunused:imports",
//  "-release:20",
  "-no-indent"
//  "-experimental"
//  "-Wall"
)

concurrentRestrictions in Global := Seq(Tags.limitAll(1))

Compile / mainClass := Some("ai.nixiesearch.main.Main")

Compile / discoveredMainClasses := Seq()

enablePlugins(DockerPlugin)

enablePlugins(BuildInfoPlugin)
buildInfoKeys ++= Seq[BuildInfoKey](
  BuildInfoKey.action("jdk") { java.lang.Runtime.version().toString },
  BuildInfoKey.action("gpu") { GPU },
  BuildInfoKey.action("arch") { PLATFORM }
)

if (GRAALVM_TRACE) {
  fork := true
  javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/ai.nixiesearch/native-image-configs"
} else {
  javaOptions ++= Nil
}

docker / dockerfile := {
  val artifact: File     = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from(s"--platform=$PLATFORM ubuntu:questing-20251007")
    runRaw(
      List(
        "apt-get update",
        "apt-get install -y --no-install-recommends openjdk-25-jdk-headless htop procps curl inetutils-ping libgomp1 locales wget",
        "sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen"
        // "rm -rf /var/lib/apt/lists/*"
      ).mkString(" && ")
    )
    runRaw(
      List(
        "wget -O /tmp/jap.tar.gz https://github.com/async-profiler/async-profiler/releases/download/v4.2/async-profiler-4.2-linux-x64.tar.gz",
        "mkdir -pv /opt/jap",
        "tar -zxf /tmp/jap.tar.gz -C /opt/jap/",
        "rm -rf /tmp/jap.tar.gz"
      ).mkString(" && ")
    )
    if (GPU) {
      runRaw(
        List(
          "wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2404/x86_64/cuda-keyring_1.1-1_all.deb",
          "dpkg -i cuda-keyring_1.1-1_all.deb",
          "apt-get update",
          "apt-get install -y --no-install-recommends cuda-toolkit-12-8 nvidia-headless-570-open cudnn9-cuda-12",
          "rm -rf /usr/lib/x86_64-linux-gnu/lib*static_v9.a",
          "rm -rf /usr/local/cuda-12.8/targets/x86_64-linux/lib/lib*.a",
          "rm -rf /opt/nvidia"
        ).mkString(" && ")
      )
    }
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

if (GPU) {
  docker / imageNames := Seq(
    ImageName(s"nixiesearch/nixiesearch:${version.value}-$PLATFORM-gpu"),
    ImageName(s"nixiesearch/nixiesearch:latest-gpu")
  )
} else {
  docker / imageNames := Seq(
    ImageName(s"nixiesearch/nixiesearch:${version.value}-$PLATFORM"),
    ImageName(s"nixiesearch/nixiesearch:latest")
  )
}

docker / buildOptions := BuildOptions(
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always
)

// Native Docker Configuration
import sbtdocker.Instructions
import sbtdocker.DockerPlugin.autoImport.*

lazy val dockerNative = taskKey[ImageId]("Build native Docker image")

dockerNative := {
  val jar      = assembly.value // Build JAR first
  val log      = streams.value.log
  val stageDir = target.value / "docker-native"
  val base     = baseDirectory.value

  val dockerfileInstructions = new Dockerfile {
    // ================================
    // Stage 1: Builder - GraalVM Native Image
    // ================================
    from(s"--platform=$PLATFORM container-registry.oracle.com/graalvm/native-image:25-muslib AS builder")

    // Set working directory
    workDir("/build")

    // Copy JAR and native-image configs
    add(jar, "/build/nixiesearch.jar")

    // Build native image with all options from Dockerfile.native
    runRaw(
      List(
        "native-image",
        "-J-Xmx16g",
        "-O2",
        "--gc=G1",
        "--enable-native-access=ALL-UNNAMED",
        "--no-fallback",
        "--verbose",
        "--static",
        "--libc=musl",
        "--initialize-at-run-time=io.netty",
        "-H:+SharedArenaSupport",
        "-H:+ReportExceptionStackTraces",
        "-H:+PrintClassInitialization",
        "-H:Name=/build/nixiesearch",
        "-H:Class=ai.nixiesearch.main.Main",
        "-jar /build/nixiesearch.jar"
        // "-H:-ReduceImplicitExceptionStackTraceInformation",
      ).mkString(" ")
    )

    // ================================
    // Stage 2: Runtime - Minimal Ubuntu
    // ================================
    from("scratch")

    // Copy native binary from builder stage
    customInstruction("COPY", "--from=builder /build/nixiesearch /nixiesearch")

    // Set entrypoint and default command
    entryPoint("/nixiesearch")
    cmd("--help")
  }

  val imageNamesSeq = Seq(
    ImageName(s"nixiesearch/nixiesearch:${version.value}-native-$PLATFORM"),
    ImageName(s"nixiesearch/nixiesearch:latest-native")
  )

  val buildOpts = BuildOptions(
    removeIntermediateContainers = BuildOptions.Remove.Always,
    pullBaseImage = BuildOptions.Pull.Always
  )

  val processor = sbtdocker.staging.DefaultDockerfileProcessor
  val imageId   = sbtdocker.DockerBuild(
    dockerfileInstructions,
    processor,
    imageNamesSeq,
    buildOpts,
    Map.empty[String, String],
    stageDir,
    "docker",
    log
  )

  log.info(s"Successfully built native Docker image: ${imageNamesSeq.head}")
  imageId
}

ThisBuild / assemblyMergeStrategy := {
  case PathList("module-info.class")                                          => MergeStrategy.discard
  case "META-INF/io.netty.versions.properties"                                => MergeStrategy.first
  case "META-INF/MANIFEST.MF"                                                 => MergeStrategy.discard
  case x if x.startsWith("META-INF/versions/")                                => MergeStrategy.first
  case x if x.startsWith("META-INF/services/")                                => MergeStrategy.concat
  case "META-INF/native-image/reflect-config.json"                            => MergeStrategy.concat
  case "META-INF/native-image/io.netty/netty-common/native-image.properties"  => MergeStrategy.first
  case "META-INF/okio.kotlin_module"                                          => MergeStrategy.first
  case "findbugsExclude.xml"                                                  => MergeStrategy.discard
  case "log4j2-test.properties"                                               => MergeStrategy.discard
  case x if x.startsWith("ai/onnxruntime/native/")                            => MergeStrategy.first
  case x if x.endsWith("/module-info.class")                                  => MergeStrategy.discard
  case x if x.startsWith("/META-INF/versions/9/org/yaml/snakeyaml/internal/") =>
    MergeStrategy.discard // pulsar client bundling snakeyaml
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

assembly / assemblyJarName          := "nixiesearch.jar"
ThisBuild / assemblyRepeatableBuild := false
ThisBuild / usePipelining           := true
packageOptions                      := Seq(ManifestAttributes(("Multi-Release", "true")))

// Benchmark sub-project
lazy val benchmark = (project in file("src/benchmark"))
  .enablePlugins(JmhPlugin)
  .dependsOn(root)
  .settings(
    name := "benchmark"
  )
