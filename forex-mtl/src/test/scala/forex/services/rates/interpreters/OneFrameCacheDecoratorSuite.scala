package forex.services.rates.interpreters

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import forex.domain._
import forex.util.{CacheAdapter, futureToIOMapper, ioToFutureMapper}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.DurationInt

class OneFrameCacheDecoratorSuite extends AnyWordSpec with Matchers with MockitoSugar {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  private val ratesService       = mock[OneFrameHttp[IO]]
  private val cacheAdapter = CacheAdapter.scaffeine[IO, Pair, Rate](futureToIOMapper, ioToFutureMapper, 5.minutes, 1000)
  private val cachedRatesService = new OneFrameCacheDecorator[IO](ratesService, cacheAdapter)

  // TODO: Rewrite this test to avoid using any matcher
  "OneFrameCacheDecorator" should {
    "work properly" in {
      val pair      = Pair(Currency.USD, Currency.EUR)
      val timestamp = Timestamp.now
      val rate      = Rate(pair, Price(1), timestamp)
      val allRates = AllPossiblePairs.map(it => Rate(it, Price(1), timestamp))

      when(ratesService.get(any(): List[Pair])).thenReturn(IO.pure(Right(allRates.map(it => it.pair -> it).toMap)))

      cachedRatesService.get(pair).unsafeRunSync() shouldEqual Right(rate)

      cachedRatesService.get(pair).unsafeRunSync() shouldEqual Right(rate)

      verify(ratesService, times(1)).get(any(): List[Pair])
    }
  }

}
