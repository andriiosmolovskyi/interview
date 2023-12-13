package forex.services.rates

import cats.implicits.toShow
import forex.domain.Pair
import org.http4s.Status

object errors {
  sealed trait Error extends Exception {
    val msg: String

    override def getMessage: String = msg
  }

  final case class OneFrameLookupNotFound private (msg: String) extends Error
  final case class OneFrameLookupFailed private (msg: String) extends Error

  def notFound(pairs: List[Pair]): Error = OneFrameLookupNotFound(
    s"Not found rates from OneFrame for pairs ${pairs.map(_.show).mkString(" ,")}"
  )

  def notFound(pair: Pair): Error = OneFrameLookupNotFound(
    s"Not found rates from OneFrame for pair ${pair.show}"
  )

  def unexpected(pairs: List[Pair], status: Status, body: String): Error = OneFrameLookupFailed(
    s"Error during getting rates from OneFrame for pairs ${pairs.map(_.show).mkString(" ,")}, " +
      s"status is: ${status.show}, response is: $body"
  )

  def cacheLookupError(pair: Pair, msg: String): OneFrameLookupFailed =
    OneFrameLookupFailed(s"Error during cache lookup for ${pair.show}, error message is: $msg")

  def cacheLookupError(pairs: List[Pair], msg: String): OneFrameLookupFailed =
    OneFrameLookupFailed(s"Error during cache lookup for ${pairs.map(_.show).mkString(" ,")}, error message is: $msg")
}
