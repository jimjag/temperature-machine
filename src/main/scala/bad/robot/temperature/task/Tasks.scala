package bad.robot.temperature.task

import java.util.concurrent.Executors._

import bad.robot.temperature.ds18b20.{SensorFile, SensorReader}
import bad.robot.temperature.rrd.RrdFile.MaxSensors
import bad.robot.temperature.rrd.{Host, RrdFile}
import bad.robot.temperature.task.Scheduler.ScheduledExecutorServiceOps
import bad.robot.temperature.{JsonExport, TemperatureWriter, XmlExport}

import scala.concurrent.duration._
import scala.language.postfixOps
import scalaz.concurrent.Task

object Tasks {

  def init(hosts: List[Host]) = {
    print(s"RRD initialising for ${hosts.map(_.name).mkString("'", "', '", "'")} (with up to $MaxSensors sensors each)...")
    Task.delay(RrdFile.exists).map {
      case false => RrdFile(hosts, 30 seconds).create()
      case _     => println("Ok")
    }
  }

  def record(source: Host, sensors: List[SensorFile], destination: TemperatureWriter) = {
    val executor = newSingleThreadScheduledExecutor(TemperatureMachineThreadFactory("reading-thread"))
    for {
      _     <- Task.delay(print(s"Monitoring sensor file(s) on '${source.name}' ${sensors.mkString("\n\t", "\n\t", "\n")}"))
      tasks <- Task.delay(executor.schedule(30 seconds, RecordTemperature(source, SensorReader(sensors), destination)))
    } yield tasks
  }

  def graphing(implicit hosts: List[Host]) = {
    val executor = newScheduledThreadPool(3, TemperatureMachineThreadFactory("graphing-thread"))
    for {
      _ <- Task.delay(executor.schedule(90 seconds, GenerateGraph(24 hours)))
      _ <- Task.delay(executor.schedule(12 hours, GenerateGraph(7 days)))
      _ <- Task.delay(executor.schedule(24 hours, GenerateGraph(30 days)))
    } yield ()
  }

  def exportXml(implicit hosts: List[Host]) = {
    val executor = newSingleThreadScheduledExecutor(TemperatureMachineThreadFactory("xml-export-thread"))
    Task.delay(executor.schedule(100 seconds, XmlExport(24 hours)))
  }

  def exportJson(implicit hosts: List[Host]) = {
    val executor = newSingleThreadScheduledExecutor(TemperatureMachineThreadFactory("json-export-thread"))
    Task.delay(executor.schedule(100 seconds, JsonExport(24 hours)))
  }

}