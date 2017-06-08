package pl.qki

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash, Terminated}
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.TreeMap
import scala.util.Random

class CacheRouter extends Actor with ActorLogging with Stash {

  private var workerMap = TreeMap.empty[Long, ActorRef]

  override def receive: Receive = {
    case g @ Get(hash) =>
      workerMap.find { case (hashFromMap, _) => hashFromMap > hash} match {
        case Some((_, ref)) =>
          ref forward g
        case None => workerMap.headOption match {
          case Some((_, r)) => r forward g
          case None => sender() ! Cached(hash, None)
        }
      }

    case Add(_, _) if workerMap.isEmpty =>
      stash()

    case add @ Add(_, hash)  =>
      workerMap.find { case (hashFromMap, _) => hashFromMap > hash} match {
        case Some((h, ref)) =>
          log.info(s"Attempt to add: $hash to: $h (workers: ${workerMap.keySet})")
          ref ! add
        case None => workerMap.headOption match {
          case Some((h, ref)) =>
            log.info(s"Attempt to add: $hash to: $h (workers: ${workerMap.keySet})")
            ref ! add
          case None => log.error(s"Cannot add: $hash to workers: ${workerMap.keySet}")
        }
      }

    case WorkerRegistration(hash) if !workerMap.contains(hash) =>
      log.info(s"Worker $hash has been registered")
      context watch sender()
      workerMap = workerMap.updated(hash, sender())
      unstashAll()

    case Terminated(a) =>
      workerMap = workerMap.filterNot {case (_, ref) => ref == a}
  }
}

object CacheRouter {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [router]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val router = system.actorOf(Props[CacheRouter], name = "router")

    import system.dispatcher

    system.scheduler.scheduleOnce(10 seconds) {
      (1 to 50).foreach {i =>
        val r = Random.nextInt(10000).toLong
        router ! Add("content-" + r, r)
      }
    }
  }
}
