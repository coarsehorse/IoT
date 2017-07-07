package IoT

import IoT.DeviceManager.RequestTrackDevice
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

/**
  * Created by User on 7/5/2017.
  */
object DeviceManager {
  def props: Props = Props(new DeviceManager)

  final case class RequestTrackDevice(groupId: String, deviceId: String)
  case object DeviceRegistered
}

class DeviceManager extends Actor with ActorLogging {
  var groupIdToActor = Map.empty[String, ActorRef]
  var actorToGroupId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("DeviceManager started")

  override def postStop(): Unit = log.info("DeviceManager stopped")

  override def receive: Receive = {
    case trkMsg @ RequestTrackDevice(groupId, _) =>
      groupIdToActor.get(groupId) match {
        case None =>
          log.info("Creating new device group actor for {}", groupId)
          val groupActor = context.actorOf(DeviceGroup.props(groupId), s"group-$groupId")
          context.watch(groupActor)
          groupActor forward trkMsg
          groupIdToActor += groupId -> groupActor
          actorToGroupId += groupActor -> groupId

        case Some(groupActor) =>
          groupActor forward trkMsg
      }

    case Terminated(groupActor) =>
      val deadId = actorToGroupId(groupActor)
      log.info("Device group actor for {} has been terminated", deadId)
      groupIdToActor -= deadId
      actorToGroupId -= groupActor
  }
}
