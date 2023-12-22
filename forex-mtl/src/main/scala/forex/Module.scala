package forex

import cats.arrow.FunctionK
import cats.effect.Async
import forex.config.ApplicationConfig
import forex.domain.{ Pair, Rate }
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import forex.util.{ CacheAdapter, SchedulerAdapter }
import fs2.io.net.Network
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.{ AutoSlash, Timeout }

import scala.concurrent.Future

class Module[F[_]: Async](config: ApplicationConfig,
                          schedulerAdapter: SchedulerAdapter[F, Unit],
                          mapper: FunctionK[Future, F],
                          mapperF: FunctionK[F, Future])(
    implicit network: Network[F]
) {

  private type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  private type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private lazy val clientResource = EmberClientBuilder.default[F].withTimeout(config.oneFrame.timeout).build

  private lazy val httpRatesService: RatesService[F]   = RatesServices.http[F](clientResource, config.oneFrame)
  private lazy val cacheAdapter = CacheAdapter.scaffeine[F, Pair, Rate](mapper, mapperF, config.cache.ttl, config.cache.size)

  private lazy val cachedRatesService: RatesService[F] = RatesServices.cached[F](httpRatesService, cacheAdapter)
  // TODO: Create additional config for cache values
  private lazy val scheduledCachedRatesService: RatesService[F] =
    RatesServices.cachedWithScheduler[F](httpRatesService, cacheAdapter, schedulerAdapter, config.cache.ttl, config.oneFrame.timeout)
  private lazy val ratesProgram: RatesProgram[F] = RatesProgram[F](
    if (config.oneFrame.schedulerMode) scheduledCachedRatesService else cachedRatesService
  )
  private lazy val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  private lazy val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private lazy val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private lazy val http: HttpRoutes[F] = ratesHttpRoutes

  lazy val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
