package forex.util

import akka.actor.ActorSystem
import cats.arrow.FunctionK
import cats.implicits._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

trait SchedulerAdapter[F[_], T] {
  def scheduleTask[A](job: Function[Unit, F[A]],
                      interval: FiniteDuration,
                      initialDelay: FiniteDuration = Duration.Zero): T
}


// TODO: Implement functional scheduler without akka
class AkkaSchedulerAdapter[F[_]](mapperF: FunctionK[F, Future])(
    implicit as: ActorSystem
) extends SchedulerAdapter[F, Unit] with Logging {
  implicit private val ec: ExecutionContext = as.dispatcher

  override def scheduleTask[A](job: Function[Unit, F[A]],
                               interval: FiniteDuration,
                               initialDelay: FiniteDuration): Unit = {
    as.scheduler.scheduleAtFixedRate(initialDelay, interval)(() => Await.result(mapperF(job(())).void, Duration.Inf))

    logger.info(s"Scheduler set for interval $interval")
  }
}

object SchedulerAdapter {
  def akka[F[_]](mapperF: FunctionK[F, Future])(implicit as: ActorSystem): AkkaSchedulerAdapter[F] =
    new AkkaSchedulerAdapter[F](mapperF)
}

