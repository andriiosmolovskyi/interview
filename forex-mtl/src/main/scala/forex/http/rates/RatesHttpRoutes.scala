package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error._
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import forex.http._
import forex.util.Logging

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] with JsonProtocol with Logging{

  import Converters._, QueryParams._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(Some(from)) +& ToQueryParam(Some(to)) =>
      rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap {
        case Right(rate) =>
          Ok(rate.asGetApiResponse)
        case Left(e: RateNotFound) =>
          NotFound(e.asJson)
        case Left(e) =>
          BadRequest(e.asJson)
      }
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      val error = RateLookupFailed(s"Error during decoding currencies for rates request, from = $from, to = $to")
      logger.warn(error.getMessage)

      BadRequest(error.asJson)
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
