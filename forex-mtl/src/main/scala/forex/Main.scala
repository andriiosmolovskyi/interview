package forex

import akka.actor.ActorSystem
import cats.arrow.FunctionK
import cats.effect._
import com.comcast.ip4s.{ Host, Port }
import forex.config._
import forex.util.{ futureToIOMapper, ioToFutureMapper, DefaultSchedulerAdaptor, SchedulerAdaptor }
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import scala.concurrent.Future

object Main extends IOApp {

  implicit private val as: ActorSystem = ActorSystem.apply()

  private val schedulerAdaptor = new DefaultSchedulerAdaptor()
  override def run(args: List[String]): IO[ExitCode] =
    Ref.of[IO, Boolean](false).flatMap { cacheFlag =>
      new Application[IO]
        .build(schedulerAdaptor, cacheFlag, futureToIOMapper, ioToFutureMapper(runtime))
        .use(_ => IO.never)
        .as(ExitCode.Success)
    }
}

class Application[F[_]: Async] {

  def build(schedulerAdaptor: SchedulerAdaptor,
            cacheBlockFlag: Ref[F, Boolean],
            mapper: FunctionK[Future, F],
            mapperF: FunctionK[F, Future])(
      implicit network: Network[F]
  ): Resource[F, Server] = {
    val config = Config.load("app")
    val module = new Module[F](config, schedulerAdaptor, cacheBlockFlag, mapper, mapperF)

    EmberServerBuilder
      .default[F]
      .withHost(Host.fromString(config.http.host).get)
      .withPort(Port.fromInt(config.http.port).get)
      .withHttpApp(module.httpApp)
      .build
  }

}
