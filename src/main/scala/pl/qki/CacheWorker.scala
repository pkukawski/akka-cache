package pl.qki

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.TreeMap
import scala.util.Random

class CacheWorker extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  lazy val workerHash = Random.nextInt(10000).toLong

  var cache = TreeMap.empty[Long, String]

  override def receive: Receive = {
    case Add(content, hash) =>
      log.info(s"Worker: $workerHash added $hash")
      cache = cache.updated(hash, content)

    case Get(hash) =>
      sender() ! Cached(hash, cache.get(hash))

    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) => register(m)

    case Rebalance(toHash, newCache) =>
      newCache ! Populate(cache.dropWhile { case (hash, _) => hash < toHash})
  }

  private def register(member: Member): Unit =
    if (member.hasRole("router"))
      context.actorSelection(RootActorPath(member.address) / "user" / "router") ! WorkerRegistration(workerHash)
}

object CacheWorker {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [worker]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[CacheWorker], name = "worker")
  }
}
