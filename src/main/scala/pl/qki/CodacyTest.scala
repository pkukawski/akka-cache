package pl.qki

import akka.actor.{Actor, ActorLogging}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class CodacyTest extends Actor with ActorLogging {

  private var list = List(1, 2,3)
  import context.dispatcher

  override def receive: Receive = {
    case some: Any =>
      longRunning.onComplete {
        case Success(_) =>
          // modification of internal state of the actor
          list = list.tail
        case Failure(t) =>
          log.error(t, "Internal error...")
      }
  }

  private def longRunning: Future[Unit] = Future.successful((): Unit)

}
