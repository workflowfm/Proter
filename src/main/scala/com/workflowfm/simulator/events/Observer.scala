package com.worklflowfm.simulator.events

import akka.actor.{ Actor, ActorRef, Props }

class Observer(f: EventHandler) extends Actor {
  def receive = {
    case Observer.SubscribeTo(publisher) => publisher ! Publisher.Subscribe
    case e: Event => f(e)
    case Publisher.Done => context.stop(self)
  }
}

object Observer {
  case class SubscribeTo(publisher: ActorRef)

  def props(f: EventHandler): Props = { Props(new Observer(f)) }
}
