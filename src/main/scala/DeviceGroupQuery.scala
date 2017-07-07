package IoT

import IoT.Device.RespondTemperature
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  case object CollectionTimeout

  def props(
             actorToDeviceId: Map[ActorRef, String],
             requestId:       Long,
             requester:       ActorRef,
             timeout:         FiniteDuration
           ): Props = {
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
  }
}

class DeviceGroupQuery(
                        actorToDeviceId: Map[ActorRef, String],
                        requestId:       Long,
                        requester:       ActorRef,
                        timeout:         FiniteDuration
                      ) extends Actor with ActorLogging {
  import DeviceGroupQuery._
  import context.dispatcher
  val queryTimeoutTimer = context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    actorToDeviceId.keysIterator.foreach { deviceActor =>
      context.watch(deviceActor)
      deviceActor ! Device.ReadTemperature(requestId = 0)
    }
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }

  override def receive: Receive = {
    waitingForReplies(
      Map.empty,
      actorToDeviceId.keySet
    )
  }

  def waitingForReplies(
                         repliesSoFar: Map[String, DeviceGroup.TemperatureReading],
                         stillWaiting: Set[ActorRef]
                       ): Receive = {
    case RespondTemperature(requestId, optValue) =>
      val reading = optValue match {
        case Some(value) =>
          DeviceGroup.Temperature(value)
        case None =>
          DeviceGroup.TemperatureNotAvailable
      }
      receivedResponse(sender(), reading, stillWaiting, repliesSoFar)

    case Terminated(deviceActor) =>
      receivedResponse(deviceActor, DeviceGroup.DeviceNotAvailable, stillWaiting, repliesSoFar)

    case CollectionTimeout =>
      val timedOutDevices = stillWaiting map { deviceActor =>
        actorToDeviceId(deviceActor) -> DeviceGroup.DeviceTimedOut
      }
      requester ! DeviceGroup.RespondAllTemperatures(requestId, repliesSoFar ++ timedOutDevices)
      context.stop(self)
  }

  def receivedResponse(
                        deviceActor:  ActorRef,
                        reading:      DeviceGroup.TemperatureReading,
                        stillWaiting: Set[ActorRef],
                        repliesSoFar: Map[String, DeviceGroup.TemperatureReading]
                      ): Unit = {
    context.unwatch(deviceActor)
    val newStillWaiting = stillWaiting - deviceActor
    val deviceId = actorToDeviceId(deviceActor)
    val newRepliesSoFar = repliesSoFar + (deviceId -> reading)
    if (newStillWaiting.isEmpty) {
      requester ! DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar)
      context.stop(self)
    }
    else
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
  }

}