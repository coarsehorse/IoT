package IoT

import akka.actor.{ ActorSystem, ActorRef }
import scala.io.StdIn

object IotApp {
	def main(args: Array[String]): Unit = {
		val system: ActorSystem = ActorSystem("root-guardian")

		try {
			// Top level supervisor 
			val iotActor: ActorRef = system.actorOf(IotSupervisor.props, "iot-actor")
			// Exit system after Enter is pressed
			StdIn.readLine()
		} finally {
			system.terminate()
		}
	}
}