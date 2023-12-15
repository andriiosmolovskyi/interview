package forex.services.rates.interpreters

import cats.arrow.FunctionK
import cats.effect.kernel.Concurrent
import cats.effect.{ Clock, Ref, Temporal }
import cats.implicits.{
  catsSyntaxApplicativeError,
  catsSyntaxApplicativeId,
  catsSyntaxEitherId,
  toFlatMapOps,
  toFunctorOps,
  toShow
}
import com.github.blemale.scaffeine.{ AsyncCache, Scaffeine }
import forex.domain.{ Pair, Rate }
import forex.services.rates.errors.{ cacheLookupError, OneFrameLookupBadResponse, OneFrameLookupNotFound }
import forex.services.rates.{ errors, Algebra }
import forex.util.{ Logging, SchedulerAdaptor }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Future }

// This approach created as alternative to OneFrameCacheDecorator to avoid additional CPU load
// Greatest solution would be create own cache for such proposes
// But it would take to much time as for test assigment
// I personally like OneFrameCacheDecorator more
class OneFrameScheduledCacheDecorator[F[_]: Temporal](underlying: Algebra[F],
                                                      schedulerAdaptor: SchedulerAdaptor,
                                                      cacheBlockFlag: Ref[F, Boolean],
                                                      mapper: FunctionK[Future, F],
                                                      mapperF: FunctionK[F, Future])
    extends Algebra[F]
    with Logging {

  // From functional requirements usage of one token is limited by 1000 times per day
  // So by using scheduler with interval 4 minutes token will be used 360 times
  // In case when external service down we will retry each request 48 times
  schedulerAdaptor.scheduleTask(() => Await.result(mapperF(cacheUpdateScheduleJob()), 4.minutes), 4.minutes)

  // According to functional requirements max TTL for the currency rate is 5 minutes
  // Because of this cache used to decrease latency and avoid unnecessary load of OneFrame
  private lazy val ratesCache = Scaffeine()
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
  override def get(request: Pair): F[Either[errors.Error, Rate]] =
    acquireCache().map(_.getFuture(request, getFromUnderlying)).flatMap(mapper.apply).attempt.map {
      case Left(error: errors.Error) => error.asLeft
      case Left(e) =>
        val error = cacheLookupError(request, e.getMessage)
        logger.error(error.getMessage)
        error.asLeft
      case Right(rate) => rate.asRight
    }

  // .getFutureAll method guarantee that mapping function will be invoked at most once
  // So we can be sure that no additional load will be on third-party providers
  // From caffeine documentation:
  // If the specified key is not already associated with a value, attempts to compute its value
  // asynchronously and enters it into this cache unless {@code null}. The entire method invocation
  // is performed atomically, so the function is applied at most once per key. If the asynchronous
  //  computation fails, the entry will be automatically removed from this cache.
  override def get(request: List[Pair]): F[Either[errors.Error, Map[Pair, Rate]]] =
    acquireCache()
      .map(_.getAllFuture(request, it => mapperF(getFromUnderlying(it))))
      .flatMap(mapper.apply)
      .attempt
      .map {
        case Left(error: errors.Error) => error.asLeft
        case Left(e) =>
          val error = cacheLookupError(request, e.getMessage)
          logger.error(error.getMessage)
          error.asLeft
        case Right(rates) => rates.view.filterKeys(request.contains).toMap.asRight
      }

  private def getFromUnderlying(pairs: Iterable[Pair]) =
    underlying.get(pairs.toList).map {
      case Right(rate) =>
        logger.info(s"Get new values from underlying: ${rate.map(_.show).mkString(" ,")}")
        rate
      case Left(e) =>
        logger.error(e)(s"Cannot get new value from underlying for pairs ${pairs.map(_.show).mkString(" ,")}")
        throw e
    }

  private def getFromUnderlying(pair: Pair) = mapperF {
    underlying.get(pair).map {
      case Right(rate) =>
        logger.info(s"Get new value from underlying: ${rate.show}")
        rate
      case Left(e) =>
        logger.error(e)(s"Cannot get new value from underlying for pair ${pair.show}")
        throw e
    }
  }

  // In case if service is not accessible it will try several times to get value from the service
  // We doing additional request only in cases when service was unavailable or internal error occurs
  // So no new token will be used for new attempt
  // In case if service down - it will block access to cache until new value will be added to the cache
  // Attempt limit is 48 because new scheduled task will be executed each 4 minutes and timeout on client is 5 seconds
  private def cacheUpdateScheduleJob(): F[Unit] =
    for {
      _ <- cacheBlockFlag.set(true)
      _ <- getForAllPairsWithRetry()
      _ <- cacheBlockFlag.set(false)
    } yield ()

  private def getForAllPairsWithRetry(attempt: Int = 1): F[Unit] =
    if (attempt <= 48) {
      Clock[F].realTime.flatMap { startTime =>
        getFromUnderlying(AllPossiblePairs)
          .map { rates =>
            logger.info(
              s"Adding new values to the cache after scheduled job execution: ${rates.map(_.show).mkString(" ,")}"
            )
            ratesCache.synchronous().putAll(rates)
          }
          .recoverWith {
            case e: OneFrameLookupNotFound =>
              logger.error(e)(s"Cannot execute update cache scheduled job, no values found in OneFrame")
              // It throws exception here because we should not receive 404 from OneFrame for AllPossiblePairs list
              throw e
            case e: OneFrameLookupBadResponse =>
              logger.error(e)(s"Cannot execute update cache scheduled job, bad response from OneFrame")
              Concurrent[F].unit
            case e =>
              logger.error(e)(s"Cannot execute update cache scheduled job, num of attempts = $attempt")
              Clock[F].realTime.flatMap { endTime =>
                val processingTime = endTime.minus(startTime)
                if (processingTime < 5.second)
                  Temporal[F].delayBy(getForAllPairsWithRetry(attempt + 1), 5.second.minus(processingTime))
                else getForAllPairsWithRetry(attempt + 1)
              }
          }
      }
    } else {
      logger.error(s"Cannot execute update cache scheduled job, limit of attempts exceeded, attempts = $attempt")
      Concurrent[F].unit
    }

  private def acquireCache(): F[AsyncCache[Pair, Rate]] =
    cacheBlockFlag.get.flatMap {
      case true =>
        Temporal[F].sleep(100.millis).flatMap(_ => acquireCache())
      case false => ratesCache.pure
    }

}
