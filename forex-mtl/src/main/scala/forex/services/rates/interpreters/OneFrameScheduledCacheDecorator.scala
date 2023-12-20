package forex.services.rates.interpreters

import cats.effect.{Clock, Temporal}
import cats.implicits._
import forex.domain.{Pair, Rate}
import forex.services.rates.errors.{OneFrameLookupBadResponse, OneFrameLookupNotFound, cacheLookupError}
import forex.services.rates.{Algebra, errors}
import forex.util._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

// This approach created as alternative to OneFrameCacheDecorator to avoid additional CPU load
// Greatest solution would be create own cache for such proposes
// But it would take too much time as for test assigment
// I personally like OneFrameCacheDecorator more
class OneFrameScheduledCacheDecorator[F[_]: Temporal](underlying: Algebra[F],
                                                      cacheAdapter: CacheAdapter[F, Pair, Rate],
                                                      schedulerAdapter: SchedulerAdapter[F, Unit],
                                                      interval: FiniteDuration)
    extends Algebra[F]
    with Logging {

  // From functional requirements usage of one token is limited by 1000 times per day
  // So by using scheduler with interval 5 minutes token will be used 288 times
  // In case when external service down we will retry each request max 60 times

  // According to functional requirements max TTL for the currency rate is 5 minutes
  // Because of this cache used to decrease latency and avoid unnecessary load of OneFrame
  private val cache: DefaultScheduledCache[F, Pair, Rate] = {
    // TODO: Create config for intervals
    ScheduledCache.default(cacheAdapter, schedulerAdapter, getForAllPairsWithTimeout, interval)
  }

  override def get(request: Pair): F[Either[errors.Error, Rate]] =
    cache.get(request).attempt.map {
      case Left(error: errors.Error) => error.asLeft
      case Left(e) =>
        val error = cacheLookupError(request, e.getMessage)
        logger.error(error.getMessage)
        error.asLeft
      case Right(rate) => rate.asRight
    }

  override def get(request: List[Pair]): F[Either[errors.Error, Map[Pair, Rate]]] =
    cache
      .getAll(request)
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

  // In case if service is not accessible it will try several times to get value from the service
  // We doing additional request only in cases when service was unavailable or internal error occurs
  // So no new token will be used for new attempt
  private def getForAllPairsWithTimeout: Function[Unit, F[Map[Pair, Rate]]] = { _ =>
    Temporal[F].timeoutTo(getForAllPairsWithRetry(), interval, Map.empty[Pair, Rate].pure[F])
  }

  private def getForAllPairsWithRetry(attempt: Int = 1): F[Map[Pair, Rate]] =
    Clock[F].realTime.flatMap { startTime =>
      getFromUnderlying(AllPossiblePairs)
        .map { rates =>
          logger.info(
            s"Adding new values to the cache after scheduled job execution: ${rates.map(_.show).mkString(" ,")}"
          )
          rates
        }
        .recoverWith {
          case e: OneFrameLookupNotFound =>
            logger.error(e)(s"Cannot execute update cache scheduled job, no values found in OneFrame")
            // It throws exception here because we should not receive 404 from OneFrame for AllPossiblePairs list
            throw e
          case e: OneFrameLookupBadResponse =>
            logger.error(e)(s"Cannot execute update cache scheduled job, bad response from OneFrame")
            Map.empty[Pair, Rate].pure[F]
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

}
