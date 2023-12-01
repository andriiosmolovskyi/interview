package forex

import cats.arrow.FunctionK
import cats.effect.IO
import cats.effect.unsafe.IORuntime

import scala.concurrent.Future

package object util {
  lazy val futureToIOMapper: FunctionK[Future, IO] =
    new FunctionK[Future, IO] {
      override def apply[A](fa: Future[A]): IO[A] = IO.fromFuture(IO(fa))
    }

  def ioToFutureMapper(implicit runtime: IORuntime): FunctionK[IO, Future] =
    new FunctionK[IO, Future] {
      override def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
    }
}
