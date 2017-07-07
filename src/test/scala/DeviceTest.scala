package IoT.test

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import scala.concurrent.duration._
import IoT.Device
import IoT.DeviceManager

class DeviceTest(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("DeviceTest"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  val probe = TestProbe()

  "device" should "reply with empty reading if no temperature is known" in {
    val deviceActor = system.actorOf(Device.props("groupId", "deviceId"), "testDevice")

    deviceActor.tell(Device.ReadTemperature(requestId = 42), probe.ref)
    val responce = probe.expectMsgType[Device.RespondTemperature]
    responce.requestId === (42)
    responce.value === (None)
  }

  "device" should "record temperature" in {
    val deviceActor = system.actorOf(Device.props("groupId", "deviceId"), "testDevice1")

    deviceActor.tell(Device.RecordTemperature(1, 15.5), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(1))

    deviceActor.tell(Device.ReadTemperature(2), probe.ref)
    val result_1 = probe.expectMsgType[Device.RespondTemperature]
    result_1.requestId === (2)
    result_1.value === (Some(15.5))

    deviceActor.tell(Device.RecordTemperature(requestId = 3, 55.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(requestId = 3))

    deviceActor.tell(Device.ReadTemperature(requestId = 4), probe.ref)
    val result_2 = probe.expectMsgType[Device.RespondTemperature]
    result_2.requestId should === (4)
    result_2.value should === (Some(55.0))
  }

  "Device " should "reply to registration requests" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(DeviceManager.RequestTrackDevice("group", "device"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    probe.lastSender should === (deviceActor)
  }

  "Device " should "ignore wrong registration requests" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.ref)
    probe.expectNoMsg(500.milliseconds)

    deviceActor.tell(DeviceManager.RequestTrackDevice("group", "Wrongdevice"), probe.ref)
    probe.expectNoMsg(500.milliseconds)
  }
}
