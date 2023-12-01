package forex.programs.rates

import forex.services.rates.errors.{OneFrameLookupFailed, OneFrameLookupNotFound, Error => RatesServiceError}

object errors {

  sealed trait Error extends Exception {
    val msg: String
  }
  object Error {
    final case class RateNotFound(msg: String) extends Error
    final case class RateLookupFailed(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case OneFrameLookupNotFound(msg) => Error.RateNotFound(msg)
    case OneFrameLookupFailed(msg)   => Error.RateLookupFailed(msg)
  }
}
