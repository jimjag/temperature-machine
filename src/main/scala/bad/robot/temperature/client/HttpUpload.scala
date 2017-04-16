package bad.robot.temperature.client

import java.net.{InetAddress, NetworkInterface}

import bad.robot.temperature.IpAddress.{unapply => _, _}
import bad.robot.temperature._
import bad.robot.temperature.client.HttpUpload.currentIpAddress
import org.http4s.Method._
import org.http4s.Status.ResponseClass.Successful
import org.http4s.Uri.{Authority, IPv4}
import org.http4s.headers.`X-Forwarded-For`
import org.http4s.util.NonEmptyList
import org.http4s.util.string._
import org.http4s.{Headers, Request, Response, Uri}

import scala.collection.JavaConverters._
import scalaz.Scalaz._
import scalaz.concurrent.Task
import scalaz.{-\/, \/, \/-}

case class HttpUpload(address: InetAddress) extends TemperatureWriter {

  private val blaze = BlazeHttpClient()

  def write(measurement: Measurement): Error \/ Unit = {
    val request = Request(PUT, Uri(
      scheme = Some("http".ci),
      authority = Some(Authority(host = IPv4(address.getHostAddress), port = Some(11900))),
      path = "/temperature"),
      headers = Headers(`X-Forwarded-For`(currentIpAddress))
    ).withBody(encode(measurement).spaces2).unsafePerformSync

    blaze.fetch(request) {
      case Successful(_)   => Task.now(\/-(()))
      case Error(response) => Task.now(-\/(UnexpectedError(s"Failed to PUT temperature data to ${request.uri.renderString}, response was ${response.status}: ${response.as[String].unsafePerformSync}")))
    }.handleWith({
      case t: Throwable    => Task.now(-\/(UnexpectedError(s"Failed to connect to $address\n\nError was $t")))
    }).unsafePerformSync
  }

}

object HttpUpload {
  val allNetworkInterfaces: List[NetworkInterface] = {
    NetworkInterface.getNetworkInterfaces
      .asScala
      .toList
      .filter(_.isUp)
      .filterNot(_.isLoopback)
  }

  val currentIpAddress: NonEmptyList[Option[InetAddress]] = {
    val addresses = for {
      interface <- allNetworkInterfaces
      address   <- interface.getInetAddresses.asScala
      ip        <- isIpAddress(address.getHostAddress).option(address)
    } yield Some(ip)

    NonEmptyList(addresses.head, addresses.tail:_*)
  }
}

object Error {
  def unapply(response: Response): Option[Response] = if (response.status.code >= 300) Some(response) else None
}