package forex.domain

import cats.Show
import cats.implicits.toShow

case class Rate(
    pair: Pair,
    price: Price,
    timestamp: Timestamp
)

object Rate {
  implicit val show: Show[Rate] = Show.show(r => s"[ pair=${r.pair.show}, price=${r.price.value.show}, timestamp=${r.timestamp.value.toString} ]")
}
