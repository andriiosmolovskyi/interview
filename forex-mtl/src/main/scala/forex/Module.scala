package forex

import cats.arrow.FunctionK
import cats.effect.{Async, Ref}
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import forex.util.SchedulerAdaptor
import fs2.io.net.Network
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.{AutoSlash, Timeout}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Module[F[_]: Async](config: ApplicationConfig,
                          schedulerAdaptor: SchedulerAdaptor,
                          cacheBlockFlag: Ref[F, Boolean],
                          mapper: FunctionK[Future, F],
                          mapperF: FunctionK[F, Future])(
    implicit network: Network[F]
) {

  private val clientResource = EmberClientBuilder.default[F].withTimeout(5.seconds).build

  private val httpRatesService: RatesService[F]   = RatesServices.http[F](clientResource, config.oneFrame)
  private val cachedRatesService: RatesService[F] = RatesServices.cached[F](httpRatesService, mapper, mapperF)
  private val scheduledCachedRatesService: RatesService[F] =
    RatesServices.cachedWithScheduler[F](httpRatesService, schedulerAdaptor, cacheBlockFlag, mapper, mapperF)
  private val ratesProgram: RatesProgram[F] = RatesProgram[F](
    if (config.oneFrame.schedulerMode) scheduledCachedRatesService else cachedRatesService
  )
  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  private type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  private type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
