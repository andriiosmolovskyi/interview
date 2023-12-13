package forex.util

import akka.actor.{ActorSystem, Cancellable}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

trait SchedulerAdaptor {
  def scheduleTask(job: Runnable, interval: FiniteDuration, initialDelay: FiniteDuration = Duration.Zero): Cancellable
}

class DefaultSchedulerAdaptor(implicit as: ActorSystem) extends SchedulerAdaptor {
  implicit private val ec: ExecutionContext = as.dispatcher

  override def scheduleTask(job: Runnable, interval: FiniteDuration, initialDelay: FiniteDuration = Duration.Zero): Cancellable = {
    as.scheduler.scheduleAtFixedRate(initialDelay, interval)(job)
  }
}
