package forex.http
package rates

import forex.domain._

object Protocol {
  final case class GetApiResponse(
      from: Currency,
      to: Currency,
      price: Price,
      timestamp: Timestamp
  )
}
