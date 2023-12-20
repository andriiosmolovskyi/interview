package forex.services.rates

import cats.arrow.FunctionK
import cats.effect.Resource
import cats.effect.kernel.{Async, Concurrent}
import forex.config.OneFrameConfig
import forex.domain.{Pair, Rate}
import forex.services.rates.interpreters._
import forex.util.{CacheAdapter, SchedulerAdapter}
import org.http4s.client.Client

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object Interpreters {
  def http[F[_]: Concurrent](client: Resource[F, Client[F]], oneFrameConfig: OneFrameConfig): Algebra[F] =
    new OneFrameHttp[F](client, oneFrameConfig)

  def cached[F[_]: Async](decorated: Algebra[F],
                          mapper: FunctionK[Future, F],
                          mapperF: FunctionK[F, Future]): Algebra[F] =
    new OneFrameCacheDecorator[F](decorated, mapper, mapperF)

  def cachedWithScheduler[F[_]: Async](decorated: Algebra[F],
                                       cacheAdapter: CacheAdapter[F, Pair, Rate],
                                       schedulerAdapter: SchedulerAdapter[F, Unit],
                                       interval: FiniteDuration): Algebra[F] =
    new OneFrameScheduledCacheDecorator[F](decorated, cacheAdapter, schedulerAdapter, interval)
}
