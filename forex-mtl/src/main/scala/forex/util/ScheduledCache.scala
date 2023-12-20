package forex.util

import cats.arrow.FunctionK
import cats.effect.Temporal
import cats.implicits.{ toFlatMapOps, toFunctorOps }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration

trait ScheduledCache[F[_], K, V] {
  def get(key: K): F[V]

  def getAll(keys: List[K]): F[Map[K, V]]
}

class DefaultScheduledCache[F[_]: Temporal, K, V](cacheAdapter: CacheAdapter[F, K, V],
                                                  schedulerAdapter: SchedulerAdapter[F],
                                                  scheduleTask: Function[Unit, F[Map[K, V]]],
                                                  mapperF: FunctionK[F, Future],
                                                  interval: FiniteDuration)
    extends ScheduledCache[F, K, V] {

  private def start(): F[Unit] = {
    val scheduleTaskWithCacheUpdate =
      scheduleTask.andThen(_.flatMap(result => cacheAdapter.set(result).map(_ => result)))

    schedulerAdapter.scheduleTask(scheduleTaskWithCacheUpdate, interval)
  }

  def get(key: K): F[V] = cacheAdapter.get(key)

  def getAll(keys: List[K]): F[Map[K, V]] = cacheAdapter.getAll(keys)

  Await.result(mapperF(start()), interval)

}

object ScheduledCache {
  def default[F[_]: Temporal, K, V](cacheAdapter: CacheAdapter[F, K, V],
                                    schedulerAdapter: SchedulerAdapter[F],
                                    scheduleTask: Function[Unit, F[Map[K, V]]],
                                    mapperF: FunctionK[F, Future],
                                    interval: FiniteDuration): DefaultScheduledCache[F, K, V] =
    new DefaultScheduledCache[F, K, V](cacheAdapter, schedulerAdapter, scheduleTask, mapperF, interval)
}
