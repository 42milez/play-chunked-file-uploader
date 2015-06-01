package helper

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.specs2.mutable.After
import org.specs2.specification.Scope

object AkkaHelper {
  class TestEnvironment(_system: ActorSystem) extends TestKit(_system) with After with ImplicitSender with Scope {
    def after = {
      _system.shutdown()
    }
  }
}
