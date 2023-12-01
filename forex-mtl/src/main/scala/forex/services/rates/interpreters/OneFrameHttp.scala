package forex.services.rates.interpreters

import cats.effect.Resource
import cats.effect.kernel.Concurrent
import cats.implicits.{catsSyntaxApplicativeError, toFunctorOps, toShow}
import cats.syntax.applicative._
import cats.syntax.either._
import forex.config.OneFrameConfig
import forex.domain.{Pair, Rate}
import forex.http.JsonProtocol
import forex.services.rates.Algebra
import forex.services.rates.errors.OneFrameLookupFailed
import forex.services.rates.errors._
import forex.util.Logging
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Header, Headers, Method, Request, Response, Status}
import org.typelevel.ci.CIString

class OneFrameHttp[F[_]: Concurrent](client: Resource[F, Client[F]], oneFrameConfig: OneFrameConfig)
    extends Algebra[F]
    with Logging
    with JsonProtocol {
  override def get(pair: Pair): F[Error Either Rate] = client.use { client =>
    val headers = Headers(Header.Raw(CIString("token"), oneFrameConfig.token))
    val request = Request[F](Method.GET, oneFrameConfig.uri / "rates" +? ("pair" -> pair.show), headers = headers)

    client
      .expectOr[Seq[Rate]](request)(responseHandler(pair))
      .map(_.headOption)
      .map(Either.fromOption[Error, Rate](_, notFound(pair)))
      .handleErrorWith {
        case e: Error => Either.left[Error, Rate](e).pure[F]
        case e => Either.left[Error, Rate](OneFrameLookupFailed(e.getMessage)).pure[F]
      }
  }

  private def responseHandler(pair: Pair): Response[F] => F[Throwable] = {
    case response if response.status == Status.NotFound =>
      val error: Throwable = notFound(pair)
      logger.error(error.getMessage)
      error.pure
    case response =>
      EntityDecoder.decodeText(response).map { body =>
        val error: Throwable = unexpected(pair, response.status, body)
        logger.error(error.getMessage)
        error
      }
  }

}
