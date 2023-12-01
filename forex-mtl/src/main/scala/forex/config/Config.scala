package forex.config

import pureconfig.ConfigSource
import pureconfig.generic.auto._

object Config {

  /**
   * @param path the property path inside the default configuration
   */
  def load(path: String): ApplicationConfig = {
    ConfigSource.default.at(path).loadOrThrow[ApplicationConfig]
  }

}
