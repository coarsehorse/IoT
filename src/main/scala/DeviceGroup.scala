package IoT

import IoT.DeviceGroup.{ReplyDeviceList, RequestAllTemperatures, RequestDeviceList}
import IoT.DeviceManager.RequestTrackDevice
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import scala.concurrent.duration._

/**
  * Created by User on 7/5/2017.
  */
object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Long)
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  final case class RequestAllTemperatures(requestId: Long)
  final case class RespondAllTemperatures(requestId: Long, temperatures: Map[String, TemperatureReading])

  sealed trait TemperatureReading
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading
}

class DeviceGroup(groupId: String) extends Actor with ActorLogging {
  var deviceIdToActor = Map.empty[String, ActorRef]
  var actorToDeviceId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("DeviceGroup {} started", groupId)
  override def postStop(): Unit = log.info("DeviceGroup {} stopped", groupId)
  override def receive: Receive = {
    case RequestAllTemperatures(requestId) =>
      context.actorOf(DeviceGroupQuery.props(
        actorToDeviceId = actorToDeviceId,
        requestId = requestId,
        requester = sender(),
        timeout = 2.seconds
      ))

    case trkMsg @ RequestTrackDevice(`groupId`, _) =>
      deviceIdToActor.get(trkMsg.deviceId) match {
        case Some(deviceActor) =>
          deviceActor forward trkMsg
        case None =>
          log.info("Creating device actor for {}", trkMsg.deviceId)
          val newDeviceActor = context.actorOf(Device.props(groupId, trkMsg.deviceId), s"device-${trkMsg.deviceId}")
          context.watch(newDeviceActor)
          deviceIdToActor += trkMsg.deviceId -> newDeviceActor
          actorToDeviceId += newDeviceActor -> trkMsg.deviceId
          newDeviceActor forward trkMsg
      }

    case RequestTrackDevice(groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevice request for groupId {}. This actor is responsible for groupId {}.",
        groupId, this.groupId
      )

    case RequestDeviceList(id) =>
      sender() ! ReplyDeviceList(id, deviceIdToActor.keySet)

    case Terminated(deviceActor) =>
      val devId: String = actorToDeviceId(deviceActor)
      log.info("Device actor for {} has been terminated", devId)
      deviceIdToActor -= devId
      actorToDeviceId -= deviceActor

  }
}