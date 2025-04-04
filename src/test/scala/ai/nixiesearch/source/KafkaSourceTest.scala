package ai.nixiesearch.source

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.IndexSourceArgs.KafkaIndexSourceArgs
import ai.nixiesearch.util.Tags.EndToEnd
import ai.nixiesearch.util.{TestDocument, TestIndexMapping}
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*

import java.util.{Collections, Properties}
import scala.util.Random
import cats.effect.unsafe.implicits.global

class KafkaSourceTest extends AnyFlatSpec with Matchers with Logging {
  val serializer: Serializer[String] = new StringSerializer()
  val doc                            = TestDocument()
  val topic                          = s"topic${Random.nextInt(10240000)}"

  import ai.nixiesearch.util.TestIndexMapping.given

  it should "receive events from kafka" taggedAs (EndToEnd.Network) in {
    val sourceConfig = KafkaIndexSourceArgs(
      brokers = List(s"localhost:9092"),
      topic = topic,
      groupId = s"nixie${Random.nextInt()}",
      offset = Some(SourceOffset.Earliest),
      index = topic
    )
    val props = new Properties()
    props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    val admin = Admin.create(props)
    admin.createTopics(Collections.singleton(new NewTopic(topic, 1, 1.toShort)))
    logger.info(s"Topic $topic created")
    val producer = new KafkaProducer[String, String](props, serializer, serializer)
    val record   = new ProducerRecord[String, String](topic, null, doc.asJson.noSpaces)
    producer.send(record).get()
    producer.flush()
    logger.info("Message sent, sleeping")
    Thread.sleep(1000)
    logger.info("Pulling single event from topic")
    val recieved = KafkaSource(sourceConfig).stream(TestIndexMapping()).take(1).compile.toList.unsafeRunSync()
    logger.info("Pull done")
    recieved shouldBe List(doc)

  }
}
