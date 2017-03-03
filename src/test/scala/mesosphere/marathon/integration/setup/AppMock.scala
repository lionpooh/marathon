package mesosphere.marathon
package integration.setup

import java.lang.management.ManagementFactory
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import org.eclipse.jetty.server.{ Request, Server }
import org.eclipse.jetty.server.handler.AbstractHandler
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import spray.client.pipelining._

import scala.concurrent.Await._
import scala.concurrent.duration._
import org.eclipse.jetty.server.{ Request, Server }
import org.eclipse.jetty.server.handler.AbstractHandler
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import com.typesafe.scalalogging.StrictLogging
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{ Request, Server }

class AppMock(appId: String, version: String, url: String) extends AbstractHandler with StrictLogging {
  import mesosphere.marathon.core.async.ExecutionContexts.global

  implicit val system = ActorSystem()
  val pipeline = sendReceive
  val waitTime = 30.seconds

  val processId = ManagementFactory.getRuntimeMXBean.getName

  def start(port: Int): Unit = {
    try {
      val server = new Server(port)
      server.setHandler(this)
      server.start()
      val taskId = System.getenv().getOrDefault("MESOS_TASK_ID", "<UNKNOWN>")
      logger.info(s"AppMock[$appId $version]: $taskId has taken the stage at port $port. Will query $url for health status.")
      server.join()
      logger.info(s"AppMock[$appId $version]: says goodbye")
    } catch {
      // exit process, if an exception is encountered
      case ex: Throwable =>
        logger.error(s"AppMock[$appId $version]: failed. Exit.", ex)
        sys.exit(1)
    }
  }

  override def handle(
    target: String,
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse): Unit = {

    if (request.getMethod == "GET" && request.getPathInfo == "/ping") {
      response.setStatus(200)
      baseRequest.setHandled(true)
      val marathonId = sys.env.getOrElse("MARATHON_APP_ID", "NO_MARATHON_APP_ID_SET")
      response.getWriter.println(s"Pong $marathonId")
    } else {
      val res = result(pipeline(Get(url)), waitTime)
      println(s"AppMock[$appId $version]: current health is $res")
      response.setStatus(res.status.intValue)
      baseRequest.setHandled(true)
      response.getWriter.print(res.entity.asString)
    }
  }
}

object AppMock {
  def main(args: Array[String]): Unit = {
    val port = args(0).toInt
    val appId = args(1)
    val version = args(2)
    val url = args(3) + "/" + port
    new AppMock(appId, version, url).start(port)
  }
}

