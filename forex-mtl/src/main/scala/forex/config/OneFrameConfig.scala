package forex.config

import org.http4s.Uri

case class OneFrameConfig(baseUri: String, token: String, schedulerMode: Boolean = true) {
  // Unsafe .get that will raise an error during application initializing
  val uri: Uri = Uri.fromString(baseUri).toOption.get
}
