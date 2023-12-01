package forex.http

import cats.effect.kernel.Concurrent
import forex.domain.Currency.show
import forex.domain._
import forex.http.rates.Protocol.GetApiResponse
import forex.programs.rates.errors
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.decoding.{EnumerationDecoder, UnwrappedDecoder}
import io.circe.generic.extras.encoding.{EnumerationEncoder, UnwrappedEncoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax.{EncoderOps, KeyOps}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}

import java.time.OffsetDateTime

trait JsonProtocol {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def valueClassEncoder[A: UnwrappedEncoder]: Encoder[A]                 = implicitly
  implicit def valueClassDecoder[A: UnwrappedDecoder]: Decoder[A]                 = implicitly
  implicit def enumEncoder[A: EnumerationEncoder]: Encoder[A]                     = implicitly
  implicit def enumDecoder[A: EnumerationDecoder]: Decoder[A]                     = implicitly
  implicit def jsonDecoder[A: Decoder, F[_]: Concurrent]: EntityDecoder[F, A]     = jsonOf[F, A]
  implicit def jsonEncoder[A: Encoder, F[_]]: EntityEncoder[F, A]                 = jsonEncoderOf[F, A]
  implicit def seqDecoder[A: Decoder, F[_]: Concurrent]: EntityDecoder[F, Seq[A]] = jsonDecoder[Seq[A], F]
  implicit def seqEncoder[A: Encoder, F[_]]: EntityEncoder[F, Seq[A]]             = jsonEncoder[Seq[A], F]
  implicit def errorEncoder[E <: errors.Error]: Encoder[E] = Encoder.instance { e =>
    Json.obj("msg" := e.msg.asJson)
  }

  implicit lazy val pairEncoder: Encoder[Pair]               = deriveConfiguredEncoder[Pair]
  implicit lazy val pairDecoder: Decoder[Pair]               = deriveConfiguredDecoder[Pair]
  implicit lazy val priceEncoder: Encoder[Price]             = Encoder.instance(_.value.asJson)
  implicit lazy val priceDecoder: Decoder[Price]             = Decoder.instance(_.value.as[BigDecimal].map(Price.apply))
  implicit lazy val responseEncoder: Encoder[GetApiResponse] = deriveConfiguredEncoder[GetApiResponse]
  implicit lazy val responseDecoder: Decoder[GetApiResponse] = deriveConfiguredDecoder[GetApiResponse]

  implicit lazy val currencyEncoder: Encoder[Currency] =
    Encoder.instance[Currency] { show.show _ andThen Json.fromString }
  implicit lazy val currencyDecoder: Decoder[Currency] =
    Decoder.instance[Currency] {
      _.value
        .as[String]
        .flatMap(
          Currency
            .fromString(_)
            .toRight(DecodingFailure.apply("Cannot parse Currency", List.empty))
        )
    }

  implicit lazy val timestampEncoder: Encoder[Timestamp] =
    Encoder.instance(_.value.asJson)
  implicit lazy val timestampDecoder: Decoder[Timestamp] =
    Decoder.instance(_.value.as[OffsetDateTime].map(Timestamp.apply))

  implicit lazy val rateEncoder: Encoder[Rate] = Encoder.instance { rate =>
    Json.obj(
      "from" := rate.pair.from.asJson,
      "to" := rate.pair.to.asJson,
      "price" := rate.price.asJson,
      "time_stamp" := rate.timestamp.asJson
    )
  }

  implicit lazy val rateDecoder: Decoder[Rate] = Decoder.instance { c =>
    val from      = c.downField("from").as[Currency]
    val to        = c.downField("to").as[Currency]
    val price     = c.downField("price").as[Price]
    val timestamp = c.downField("time_stamp").as[Timestamp]

    for {
      from <- from
      to <- to
      price <- price
      timestamp <- timestamp
    } yield Rate(Pair(from, to), price, timestamp)
  }

}
