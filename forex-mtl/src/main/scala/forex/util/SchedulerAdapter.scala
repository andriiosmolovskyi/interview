package forex.util

import akka.actor.ActorSystem
import cats.arrow.FunctionK
import cats.effect.Temporal
import cats.implicits._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

trait SchedulerAdapter[F[_]] {
  def scheduleTask[A](job: Function[Unit, F[A]],
                      interval: FiniteDuration,
                      initialDelay: FiniteDuration = Duration.Zero): F[Unit]
}


// TODO: Implement functional scheduler without akka
class AkkaSchedulerAdapter[F[_]: Temporal](mapperF: FunctionK[F, Future])(
    implicit as: ActorSystem
) extends SchedulerAdapter[F] {
  implicit private val ec: ExecutionContext = as.dispatcher

  override def scheduleTask[A](job: Function[Unit, F[A]],
                               interval: FiniteDuration,
                               initialDelay: FiniteDuration): F[Unit] =
    as.scheduler
      .scheduleAtFixedRate(initialDelay, interval)(() => Await.result(mapperF(job.apply(())).void, interval))
      .pure[F]
      .void
}

object SchedulerAdapter {
  def akka[F[_]: Temporal](mapperF: FunctionK[F, Future])(implicit as: ActorSystem): AkkaSchedulerAdapter[F] =
    new AkkaSchedulerAdapter[F](mapperF)
}

