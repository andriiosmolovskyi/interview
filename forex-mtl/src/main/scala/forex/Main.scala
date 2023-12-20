package forex

import akka.actor.ActorSystem
import cats.arrow.FunctionK
import cats.effect._
import com.comcast.ip4s.{ Host, Port }
import forex.config._
import forex.util.{ futureToIOMapper, ioToFutureMapper, SchedulerAdapter }
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import scala.concurrent.Future

object Main extends IOApp {

  implicit private val as: ActorSystem = ActorSystem.apply()

  private val schedulerAdaptor = SchedulerAdapter.akka(ioToFutureMapper(runtime))
  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO]
      .build(schedulerAdaptor, futureToIOMapper, ioToFutureMapper(runtime))
      .use(_ => IO.never)
      .as(ExitCode.Success)
}

class Application[F[_]: Async] {

  def build(schedulerAdaptor: SchedulerAdapter[F], mapper: FunctionK[Future, F], mapperF: FunctionK[F, Future])(
      implicit network: Network[F]
  ): Resource[F, Server] = {
    val config = Config.load("app")
    val module = new Module[F](config, schedulerAdaptor, mapper, mapperF)

    EmberServerBuilder
      .default[F]
      .withHost(Host.fromString(config.http.host).get)
      .withPort(Port.fromInt(config.http.port).get)
      .withHttpApp(module.httpApp)
      .build
  }

}
