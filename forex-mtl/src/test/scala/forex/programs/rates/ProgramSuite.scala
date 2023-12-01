package forex.programs.rates

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import forex.domain._
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.toProgramError
import forex.services.RatesService
import forex.services.rates.errors.OneFrameLookupFailed
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class ProgramSuite extends AnyWordSpec with Matchers with MockitoSugar {

  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  private val ratesService = mock[RatesService[IO]]

  private val ratesProgram = new Program[IO](ratesService)

  "Program" should {
    "work properly" in {
      val pair      = Pair(Currency.USD, Currency.EUR)
      val request   = GetRatesRequest(pair.from, pair.to)
      val timestamp = Timestamp.now
      val rate      = Rate(pair, Price(1), timestamp)

      when(ratesService.get(pair)).thenReturn(IO.pure(Right(rate)))

      ratesProgram.get(request).unsafeRunSync() shouldEqual Right(rate)
    }

    "work properly in case of error" in {
      val pair    = Pair(Currency.USD, Currency.EUR)
      val request = GetRatesRequest(pair.from, pair.to)
      val error   = OneFrameLookupFailed("msg")

      when(ratesService.get(pair)).thenReturn(IO.pure(Left(error)))

      ratesProgram.get(request).unsafeRunSync() shouldEqual Left(toProgramError(error))
    }
  }
}
