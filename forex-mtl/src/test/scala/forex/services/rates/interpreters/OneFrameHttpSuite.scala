package forex.services.rates.interpreters

import cats.effect.unsafe.IORuntime
import cats.effect.{ IO, Resource }
import forex.config.OneFrameConfig
import forex.domain._
import org.http4s.client.Client
import org.http4s.{ EntityDecoder, Request, Response }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class OneFrameHttpSuite extends AnyWordSpec with Matchers with MockitoSugar {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  private val oneFrameConfig                 = OneFrameConfig("http://localhost", "token")
  private val client                         = mock[Client[IO]]
  private val clientResource                 = Resource.pure[IO, Client[IO]](client)
  private val ratesService: OneFrameHttp[IO] = new OneFrameHttp[IO](clientResource, oneFrameConfig)

  "OneFrameHttp" should {
    "work properly" in {
      val pair      = Pair(Currency.USD, Currency.EUR)
      val timestamp = Timestamp.now
      val rate      = Rate(pair, Price(1), timestamp)

      when(
        client
          .expectOr[Seq[Rate]](any[Request[IO]])(any[Response[IO] => IO[Throwable]])(any[EntityDecoder[IO, Seq[Rate]]])
      ).thenReturn(IO.pure(Seq(rate)))

      val actual = ratesService.get(pair).unsafeRunSync()

      actual shouldEqual Right(rate)
    }
  }

}
