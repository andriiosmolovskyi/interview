package forex.services.rates.interpreters

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import forex.domain._
import forex.util.{futureToIOMapper, ioToFutureMapper}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class OneFrameCacheDecoratorSuite extends AnyWordSpec with Matchers with MockitoSugar {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  private val ratesService       = mock[OneFrameHttp[IO]]
  private val cachedRatesService = new OneFrameCacheDecorator[IO](ratesService, futureToIOMapper, ioToFutureMapper)

  "OneFrameCacheDecorator" should {
    "work properly" in {
      val pair      = Pair(Currency.USD, Currency.EUR)
      val timestamp = Timestamp.now
      val rate      = Rate(pair, Price(1), timestamp)

      when(ratesService.get(pair)).thenReturn(IO.pure(Right(rate)))

      cachedRatesService.get(pair).unsafeRunSync() shouldEqual Right(rate)

      cachedRatesService.get(pair).unsafeRunSync() shouldEqual Right(rate)

      verify(ratesService, times(1)).get(pair)
    }
  }

}
