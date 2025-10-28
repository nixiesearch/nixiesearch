package ai.nixiesearch

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Measurement, Mode, OutputTimeUnit, Scope, State, Warmup}

import java.util.concurrent.TimeUnit

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
class DocumentDecoderBenchmark {

  @Benchmark
  def measureDocumentDecoder() = {
    1
  }

}
