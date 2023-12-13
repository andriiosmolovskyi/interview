package forex.services.rates.interpreters

import cats.arrow.FunctionK
import cats.syntax.either._
import cats.effect.kernel.Concurrent
import cats.implicits.{ catsSyntaxApplicativeError, catsSyntaxEitherId, toFunctorOps, toShow }
import com.github.blemale.scaffeine.Scaffeine
import forex.domain.{ Pair, Rate }
import forex.services.rates.errors.cacheLookupError
import forex.services.rates.{ errors, Algebra }
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


  // Each time when we need to get value we will send a request to get all possible pairs and store them in to cache
  // It is not so efficient as it could be (because each time when we need just one pair we load from memory all 72)
  // But from my perspective it is not so bad
  override def get(request: Pair): F[Either[errors.Error, Rate]] =
    get(AllPossiblePairs).map(
      _.map(_.get(request)).flatMap(Either.fromOption(_, cacheLookupError(request, "Value isn't present in cache")))
    )

  // .getFutureAll method guarantee that mapping function will be invoked at most once
  // So we can be sure that no additional load will be on third-party providers
  // From caffeine documentation:
  // If the specified key is not already associated with a value, attempts to compute its value
  // asynchronously and enters it into this cache unless {@code null}. The entire method invocation
  // is performed atomically, so the function is applied at most once per key. If the asynchronous
  //  computation fails, the entry will be automatically removed from this cache.
  override def get(request: List[Pair]): F[Either[errors.Error, Map[Pair, Rate]]] =
    mapper(ratesCache.getAllFuture(AllPossiblePairs, getFromUnderlying)).attempt.map {
      case Left(error: errors.Error) => error.asLeft
      case Left(e) =>
        val error = cacheLookupError(request, e.getMessage)
        logger.error(error.getMessage)
        error.asLeft
      case Right(rates) => rates.view.filterKeys(request.contains).toMap.asRight
    }

  private def getFromUnderlying(pairs: Iterable[Pair]) = mapperF {
    underlying.get(pairs.toList).map {
      case Right(rate) =>
        logger.info(s"Adding new values to the cache: ${rate.map(_.show).mkString(" ,")}")
        rate
      case Left(e) =>
        logger.error(e)(s"Cannot put new value to the cache for pairs ${pairs.map(_.show).mkString(" ,")}")
        throw e
    }
  }

}
