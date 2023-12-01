package forex.http.rates

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.toShow
import forex.domain._
import forex.http._
import forex.http.rates.Protocol.GetApiResponse
import forex.programs.RatesProgram
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.Error.{ RateLookupFailed, RateNotFound }
import io.circe.Json
import io.circe.syntax.{ EncoderOps, KeyOps }
import org.http4s.{ EntityDecoder, Method, Request, Response, Status, Uri }
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class RatesHttpRoutesSuite extends AnyWordSpec with Matchers with MockitoSugar with JsonProtocol {

  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  private val ratesProgram = mock[RatesProgram[IO]]

  private val routes = new RatesHttpRoutes[IO](ratesProgram).routes

  "RatesHttpRoutes" should {
    "work properly" in {
      val pair      = Pair(Currency.USD, Currency.EUR)
      val request   = GetRatesRequest(pair.from, pair.to)
      val timestamp = Timestamp.now
      val rate      = Rate(pair, Price(1), timestamp)

      val getRatesResponse = GetApiResponse(pair.from, pair.to, Price(1), timestamp)

      val expectedJson = getRatesResponse.asJson

      when(ratesProgram.get(request)).thenReturn(IO.pure(Right(rate)))

      val response = routes
        .run {
          Request(Method.GET, Uri.fromString(s"/rates?from=${pair.from.show}&to=${pair.to.show}").toOption.get)
        }
        .getOrRaise(new NullPointerException())

      check[Json](response, Status.Ok, Some(expectedJson)) shouldBe true
    }

    "work properly when rate not found" in {
      val pair    = Pair(Currency.USD, Currency.EUR)
      val request = GetRatesRequest(pair.from, pair.to)
      val error   = RateNotFound("Not found")

      val expectedJson = Json.obj(
        "msg" := "Not found"
      )

      when(ratesProgram.get(request)).thenReturn(IO.pure(Left(error)))

      val response = routes
        .run {
          Request(Method.GET, Uri.fromString(s"/rates?from=${pair.from.show}&to=${pair.to.show}").toOption.get)
        }
        .getOrRaise(new NullPointerException())

      check[Json](response, Status.NotFound, Some(expectedJson)) shouldBe true
    }

    "work properly when other error occurs" in {
      val pair    = Pair(Currency.USD, Currency.EUR)
      val request = GetRatesRequest(pair.from, pair.to)
      val error   = RateLookupFailed("Error")

      val expectedJson = Json.obj(
        "msg" := "Error"
      )

      when(ratesProgram.get(request)).thenReturn(IO.pure(Left(error)))

      val response = routes
        .run {
          Request(Method.GET, Uri.fromString(s"/rates?from=${pair.from.show}&to=${pair.to.show}").toOption.get)
        }
        .getOrRaise(new NullPointerException())

      check[Json](response, Status.BadRequest, Some(expectedJson)) shouldBe true
    }
  }

  // Return true if match succeeds; otherwise false
  private def check[A](actual: IO[Response[IO]], expectedStatus: Status, expectedBody: Option[A])(
      implicit ev: EntityDecoder[IO, A],
      runtime: IORuntime
  ): Boolean = {
    val actualResp  = actual.unsafeRunSync()
    val statusCheck = actualResp.status == expectedStatus
    val bodyCheck = expectedBody.fold[Boolean](
      // Verify Response's body is empty.
      actualResp.body.compile.toVector.unsafeRunSync().isEmpty
    )(
      expected => actualResp.as[A].unsafeRunSync() == expected
    )

    statusCheck && bodyCheck
  }
}
