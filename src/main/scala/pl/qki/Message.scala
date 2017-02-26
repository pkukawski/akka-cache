package pl.qki

import akka.actor.ActorRef

import scala.collection.immutable.TreeMap

trait Message
case class Add(str: String, hash: Long) extends Message
case class Get(hash: Long) extends Message
case class Populate(cache: TreeMap[Long, String])
case object GetHash
case class Hash(code: Long)
case class Rebalance(toHash: Long, newWorker: ActorRef)

case class Cached(hash :Long, content: Option[String])
case class WorkerRegistration(hash: Long)