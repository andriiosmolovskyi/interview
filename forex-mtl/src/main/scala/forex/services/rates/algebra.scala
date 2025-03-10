package forex.services.rates

import forex.domain.{Pair, Rate}
import errors._

trait Algebra[F[_]] {
  def get(pair: Pair): F[Error Either Rate]
  def get(pairs: List[Pair]): F[Error Either Map[Pair, Rate]]
}
