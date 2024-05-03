package ai.nixiesearch.index.store
import ai.nixiesearch.index.manifest.IndexManifest
import cats.effect.{IO, Resource}
import software.amazon.awssdk.services.s3.S3AsyncClient

case class S3StateClient(client: S3AsyncClient) extends StateClient {
  override def manifest(): IO[IndexManifest] = ???

  override def read(fileName: String): fs2.Stream[IO, Byte] = ???

  override def write(fileName: String, stream: fs2.Stream[IO, Byte]): IO[Unit] = ???

  override def delete(fileName: String): IO[Unit] = ???

  override def close(): IO[Unit] = ???
}

object S3StateClient {
  def create(bucket: String, prefix: String, region: String, endpoint: Option[String] = None): IO[S3StateClient] = ???
}
