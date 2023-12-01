package forex.http

import forex.domain.{ Currency, Pair, Price, Rate, Timestamp }
import forex.http.rates.Protocol.GetApiResponse
import io.circe.syntax.EncoderOps
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonProtocolSuite extends AnyWordSpec with Matchers {

  "JsonProtocol" should {
    "properly encode/decode Seq[Rate]" in new JsonProtocol {
      val rate: Rate = Rate(Pair(Currency.USD, Currency.EUR), Price(1), Timestamp.now)

      rate.asJson.as[Rate] shouldEqual Right(rate)
    }
    "properly encode/decode GetApiResponse" in new JsonProtocol {
      val response: GetApiResponse = GetApiResponse(Currency.USD, Currency.EUR, Price(1), Timestamp.now)

      response.asJson.as[GetApiResponse] shouldEqual Right(response)
    }
  }

}
