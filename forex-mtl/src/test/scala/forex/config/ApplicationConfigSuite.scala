package forex.config

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ApplicationConfigSuite extends AnyWordSpec with Matchers {
  "Config" should {
    "be loadable" in {
      noException should be thrownBy Config.load("app")
    }
  }

}
