package forex.services.rates.interpreters

import cats.effect.kernel.Concurrent
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxEitherId, toFunctorOps, toShow}
import cats.syntax.either._
import forex.domain.{Pair, Rate}
import forex.services.rates.errors.cacheLookupError
import forex.services.rates.{Algebra, errors}
import forex.util.{CacheAdapter, Logging}

class OneFrameCacheDecorator[F[_]: Concurrent](underlying: Algebra[F], cacheAdapter: CacheAdapter[F, Pair, Rate])
    extends Algebra[F]
    with Logging {

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
    cacheAdapter.getAllWithFallback(AllPossiblePairs, getFromUnderlying).attempt.map {
      case Left(error: errors.Error) => error.asLeft
      case Left(e) =>
        val error = cacheLookupError(request, e.getMessage)
        logger.error(error.getMessage)
        error.asLeft
      case Right(rates) => rates.view.filterKeys(request.contains).toMap.asRight
    }

  private def getFromUnderlying(pairs: List[Pair]): F[Map[Pair, Rate]] =
    underlying.get(pairs).map {
      case Right(rate) =>
        logger.info(s"Adding new values to the cache: ${rate.map(_.show).mkString(" ,")}")
        rate
      case Left(e) =>
        logger.error(e)(s"Cannot put new value to the cache for pairs ${pairs.map(_.show).mkString(" ,")}")
        throw e
    }

}
