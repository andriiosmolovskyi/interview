package forex.domain

import cats.Show

object Currency extends Enumeration {
  //TODO: Find better name
  type Currency = Value

  val AUD, CAD, CHF, EUR, GBP, NZD, JPY, SGD, USD = Value

  implicit val show: Show[Currency] = Show.show(_.toString)

  // TODO: Define safe method which will use .withName method of enum
  def fromString(s: String): Option[Currency] = s.toUpperCase match {
    case "AUD" => Some(AUD)
    case "CAD" => Some(CAD)
    case "CHF" => Some(CHF)
    case "EUR" => Some(EUR)
    case "GBP" => Some(GBP)
    case "NZD" => Some(NZD)
    case "JPY" => Some(JPY)
    case "SGD" => Some(SGD)
    case "USD" => Some(USD)
    case _     => None
  }

}
