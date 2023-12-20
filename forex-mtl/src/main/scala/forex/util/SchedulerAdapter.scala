package forex.util

import akka.actor.ActorSystem
import cats.arrow.FunctionK
import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Temporal}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

trait SchedulerAdapter[F[_]] {
  def scheduleTask[A](job: Function[Unit, F[A]],
                      interval: FiniteDuration,
                      initialDelay: FiniteDuration = Duration.Zero): F[Unit]
}

class AkkaSchedulerAdapter[F[_]: Temporal](mapperF: FunctionK[IO, Future], mapper: FunctionK[F, IO])(
    implicit as: ActorSystem, runtime: IORuntime
) extends SchedulerAdapter[IO] {
  implicit private val ec: ExecutionContext = as.dispatcher

  override def scheduleTask[A](job: Function[Unit, F[A]],
                               interval: FiniteDuration,
                               initialDelay: FiniteDuration): F[Unit] = {
    val result = mapper(job.apply(())).unsafeRunSync()

    println(ec)
    println(result)
    Temporal[F].unit
  }
}

object SchedulerAdapter {
  def akka[F[_]: Temporal](mapperF: FunctionK[F, Future])(implicit as: ActorSystem): AkkaSchedulerAdapter[F] =
    new AkkaSchedulerAdapter[F](mapperF)
}
