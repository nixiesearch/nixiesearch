package ai.nixiesearch.util

import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.util.Distance.DistanceUnit
import io.circe.{Decoder, Encoder, Json}

import scala.util.{Failure, Success, Try}

case class Distance(value: Double, unit: DistanceUnit) {
  def meters = value * unit.scale
}

object Distance {
  given distanceEncoder: Encoder[Distance] = Encoder.instance(c => Json.fromString(s"${c.value} ${c.unit.name}"))

  def decode(number: String, unit: String): Try[Distance] = {
    number.toDoubleOption match {
      case None                       => Failure(UserError(s"cannot parse number part '$number' of distance"))
      case Some(double) if double < 0 => Failure(UserError(s"distance cannot be negative: got $number $unit"))
      case Some(double) =>
        DistanceUnit.distanceNames.get(unit) match {
          case Some(u) => Success(Distance(double, u))
          case None    => Failure(UserError(s"distance unit '$unit' not supported"))
        }
    }

  }
  given distanceDecoder: Decoder[Distance] = Decoder.decodeString.emapTry(string => {
    val sepPosition = string.indexOf(' ')
    if (sepPosition > 0) {
      val number = string.substring(0, sepPosition)
      val scale  = string.substring(sepPosition).strip()
      decode(number, scale)
    } else {
      val lastDigitIndex = string.lastIndexWhere(c => (c >= '0') && (c <= '9'))
      if (lastDigitIndex >= 0) {
        val number = string.substring(0, lastDigitIndex + 1)
        val scale  = string.substring(lastDigitIndex + 1).strip()
        decode(number, scale)
      } else {
        Failure(UserError(s"cannot parse distance '$string'"))
      }
    }
  })
  sealed trait DistanceUnit {
    def name: String
    def forms: List[String]
    def scale: Double
  }

  object DistanceUnit {
    case object Kilometer extends DistanceUnit {
      val name  = "km"
      val forms = List("kms", "kilometer", "kilometers")
      val scale = 1000.0
    }

    case object Meter extends DistanceUnit {
      val name  = "m"
      val forms = List("meter", "meters")
      val scale = 1.0
    }

    case object Millimeter extends DistanceUnit {
      val name  = "mm"
      val forms = List("millimeter", "millimeters")
      val scale = 0.001
    }

    case object Centimeter extends DistanceUnit {
      val name  = "cm"
      val forms = List("centimeter", "centimeters")
      val scale = 0.01
    }

    case object Mile extends DistanceUnit {
      val name  = "mi"
      val forms = List("mile", "miles")
      val scale = 1609.344
    }

    case object Yard extends DistanceUnit {
      val name  = "yd"
      val forms = List("yard", "yards")
      val scale = 0.9144
    }

    case object Foot extends DistanceUnit {
      val name  = "ft"
      val forms = List("foot", "feet")
      val scale = 0.3048
    }

    case object Inch extends DistanceUnit {
      val name  = "in"
      val forms = List("inch", "inches")
      val scale = 0.0254
    }

    case object NauticalMile extends DistanceUnit {
      val name = "nmi"
      val forms =
        List("nauticalmiles", "nauticalmile", "nautical-mile", "nautical-miles", "nautical mile", "nautical miles")
      val scale = 1852.0
    }

    case object Parsec extends DistanceUnit {
      val name  = "pc"
      val forms = List("parsec", "parsecs")
      val scale = 3.0857e16
    }

    case object AstronomicalUnit extends DistanceUnit {
      val name  = "au"
      val forms = List("astronomical-unit", "astronomical units")
      val scale = 1.495978707e11
    }

    case object LightYear extends DistanceUnit {
      val name  = "ly"
      val forms = List("light-year", "light-years", "lightyear", "lightyears", "light year", "light years")
      val scale = 9.4607e15
    }

    val distances: List[DistanceUnit] = List(
      Kilometer,
      Meter,
      Millimeter,
      Centimeter,
      Mile,
      Yard,
      Foot,
      Inch,
      NauticalMile,
      Parsec,
      AstronomicalUnit,
      LightYear
    )
    val distanceNames = distances.flatMap(du => (du.forms :+ du.name).map(name => name -> du)).toMap

    given distanceUnitEncoder: Encoder[DistanceUnit] = Encoder.instance(du => Json.fromString(du.name))

    given distanceUnitDecoder: Decoder[DistanceUnit] = Decoder.decodeString.emapTry(name =>
      distanceNames.get(name.toLowerCase) match {
        case Some(value) => Success(value)
        case None        => Failure(UserError(s"unit name '$name' not suppported"))
      }
    )
  }

}
