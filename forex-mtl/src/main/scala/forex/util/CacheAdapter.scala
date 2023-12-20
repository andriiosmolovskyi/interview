package forex.util

import cats.arrow.FunctionK
import cats.effect.Temporal
import cats.implicits.{catsSyntaxApplicativeId, toFlatMapOps, toFunctorOps, toTraverseOps}
import com.github.blemale.scaffeine.{AsyncCache, Scaffeine}

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait CacheAdapter[F[_], K, V] {
  // Blocks on empty
  def get(key: K): F[V]
  // Blocks on empty
  def getAll(keys: List[K]): F[Map[K, V]]
  def set(entities: Map[K, V]): F[Unit]
}

class SCaffeineCacheAdapter[F[_]: Temporal, K, V](asyncCache: AsyncCache[K, V],
                                                  mapper: FunctionK[Future, F],
                                                  mapperF: FunctionK[F, Future])
  extends CacheAdapter[F, K, V] {

  override def get(key: K): F[V] = asyncCache.getIfPresent(key).map(mapper.apply).traverse(identity).flatMap {
    case Some(value) => value.pure[F]
    // Not the best solution, should be replaced with something based on Deferred, MVar or similar,
    // so it will block get until value will be available and don't lookup every time
    case None => Temporal[F].sleep(100.millis).flatMap(_ => get(key))
  }

  override def getAll(keys: List[K]): F[Map[K, V]] = mapper {
    asyncCache.getAllFuture(
      keys, {
        case keys if keys.nonEmpty =>
          // Not the best solution, should be replaced with something based on Deferred, MVar or similar,
          // so it will block get until value will be available and don't lookup every time
          mapperF(Temporal[F].sleep(100.millis).flatMap(_ => getAll(keys.toList)))
        case _ => Future.successful(Map.empty)
      }
    )
  }

  override def set(entities: Map[K, V]): F[Unit] =
    asyncCache.synchronous().pure[F].map(_.putAll(entities))
}


object CacheAdapter {
  def scaffeine[F[_]: Temporal, K, V](mapper: FunctionK[Future, F],
              mapperF: FunctionK[F, Future],
                    expirationTimeout: FiniteDuration,
                    cacheSize: Long): SCaffeineCacheAdapter[F, K, V] = {

     val cache = Scaffeine()
      .expireAfterWrite(expirationTimeout)
      .maximumSize(cacheSize)
      .buildAsync[K, V]()

    new SCaffeineCacheAdapter(cache, mapper, mapperF)
  }
}