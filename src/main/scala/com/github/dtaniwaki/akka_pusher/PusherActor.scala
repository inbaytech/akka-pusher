package com.github.dtaniwaki.akka_pusher

import akka.actor._
import akka.pattern.pipe
import com.github.dtaniwaki.akka_pusher.PusherMessages._
import com.typesafe.config.{ Config, ConfigFactory }
import spray.json.DefaultJsonProtocol._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable.Queue
import org.slf4j.LoggerFactory
import net.ceedubs.ficus.Ficus._

import scala.util.{ Success, Failure }

class PusherActor(config: Config = ConfigFactory.load()) extends Actor {
  implicit val system = context.system
  implicit val ec: ExecutionContext = system.dispatcher
  private lazy val logger = LoggerFactory.getLogger(getClass)

  val batchNumber = 100
  val batchTrigger = config.as[Option[Boolean]]("pusher.batchTrigger").getOrElse(false)
  val batchInterval = Duration(config.as[Option[Int]]("pusher.batchInterval").getOrElse(1000), MILLISECONDS)
  protected val batchTriggerQueue = Queue[BatchTriggerMessage]()
  protected val scheduler = if (batchTrigger) {
    Some(system.scheduler.schedule(
      batchInterval,
      batchInterval,
      self,
      BatchTriggerTick()))
  } else {
    None
  }

  logger.debug("PusherActor configuration:")
  logger.debug(s"batchTrigger........ ${batchTrigger}")
  logger.debug(s"batchInterval....... ${batchInterval}")

  val pusher = new PusherClient()

  override def receive: Receive = PartialFunction { message =>
    val res = message match {
      case TriggerMessage(channel, event, message, socketId) =>
        pusher.trigger(channel, event, message, socketId)
      case ChannelMessage(channel, attributes) =>
        pusher.channel(channel, attributes)
      case ChannelsMessage(prefixFilter, attributes) =>
        pusher.channels(prefixFilter, attributes)
      case UsersMessage(channel) =>
        pusher.users(channel)
      case AuthenticateMessage(channel, socketId, data) =>
        pusher.authenticate(channel, socketId, data)
      case ValidateSignatureMessage(key, signature, body) =>
        pusher.validateSignature(key, signature, body)
      case trigger: BatchTriggerMessage if batchTrigger =>
        batchTriggerQueue.enqueue(trigger)
        true
      case BatchTriggerTick() if batchTrigger =>
        val triggers = batchTriggerQueue.dequeueAll { _ => true }
        triggers.grouped(batchNumber) foreach { triggers =>
          pusher.trigger(triggers.map(BatchTriggerMessage.unapply(_).get)).map {
            case Success(_) => // Do Nothing
            case Failure(e) => logger.warn(e.getMessage)
          }
        }
        triggers.length
      case _ =>
    }
    if (!sender.eq(system.deadLetters) && !sender.eq(ActorRef.noSender)) {
      res match {
        case future: Future[_] =>
          future pipeTo sender
        case res if !res.isInstanceOf[Unit] =>
          sender ! res
        case _ =>
      }
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    if (batchTrigger) {
      scheduler.map(_.cancel())
    }
    pusher.shutdown()
  }
}

object PusherActor {
  def props(): Props = Props(new PusherActor())
}
