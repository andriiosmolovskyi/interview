package forex.services.rates.interpreters

import cats.arrow.FunctionK
import cats.effect.kernel.Concurrent
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxEitherId, toFunctorOps, toShow}
import com.github.blemale.scaffeine.Scaffeine
import forex.domain.{Pair, Rate}
import forex.services.rates.errors.cacheLookupError
import forex.services.rates.{Algebra, errors}
import forex.util.Logging

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OneFrameCacheDecorator[F[_]: Concurrent](underlying: Algebra[F],
                                          mapper: FunctionK[Future, F],
                                          mapperF: FunctionK[F, Future])
    extends Algebra[F]
    with Logging {

  // According to functional requirements max TTL for the currency rate is 5 minutes
  // Because of this cache used to decrease latency and avoid unnecessary load of OneFrame
  private val ratesCache = Scaffeine()
    .expireAfterWrite(5.minutes)
    // The key is a pair of Currencies, for now 9 currencies were defined,
    // max size of cache is amount of combinations from 9 by 2 = 72, so 1000 is more then enough
    // Can be increased for future needs
    .maximumSize(1000)
    .buildAsync[Pair, Rate]()

  // .getFuture method guarantee that mapping function will be invoked at most once
  // So we can be sure that no additional load will be on third-party providers
  // From caffeine documentation:
  // If the specified key is not already associated with a value, attempts to compute its value
  // asynchronously and enters it into this cache unless {@code null}. The entire method invocation
  // is performed atomically, so the function is applied at most once per key. If the asynchronous
  //  computation fails, the entry will be automatically removed from this cache.
  override def get(request: Pair): F[Either[errors.Error, Rate]] = {
    mapper(ratesCache.getFuture(request, getFromUnderlying)).attempt.map {
      case Left(error: errors.Error) => error.asLeft
      case Left(e) =>
        val error = cacheLookupError(request, e.getMessage)
        logger.error(error.getMessage)
        error.asLeft
      case Right(rate) => rate.asRight
    }
  }

  private def getFromUnderlying(pair: Pair) = mapperF {
    underlying.get(pair).map {
      case Right(rate) =>
        logger.info(s"Adding new value to the cache: ${rate.show}")
        rate
      case Left(e) =>
        logger.error(e)(s"Cannot put new value to the cache")
        throw e
    }
  }
}
