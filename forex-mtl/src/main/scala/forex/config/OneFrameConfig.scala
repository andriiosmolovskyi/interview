package forex.config

import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

case class OneFrameConfig(baseUri: String, token: String, timeout: FiniteDuration, schedulerMode: Boolean = true) {
  // Unsafe .get that will raise an error during application initializing
  val uri: Uri = Uri.fromString(baseUri).toOption.get
}
