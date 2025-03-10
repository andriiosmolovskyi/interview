package forex.domain

import cats.Show
import cats.implicits.toShow

final case class Pair(
    from: Currency.Currency,
    to: Currency.Currency
)

object Pair {
  implicit val show: Show[Pair] = Show.show(pair => pair.from.show + pair.to.show)
}
