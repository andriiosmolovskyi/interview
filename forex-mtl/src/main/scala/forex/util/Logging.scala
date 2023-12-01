package forex.util

import org.log4s.{Logger, getLogger}

trait Logging {
  implicit protected lazy val logger: Logger =
    getLogger(loggerName.replace(".package$", ".").stripSuffix("$"))

  private def loggerName: String = getClass.getName
}
