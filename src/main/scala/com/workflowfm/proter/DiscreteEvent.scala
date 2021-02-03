package com.workflowfm.proter

import akka.actor.ActorRef
import com.workflowfm.proter.controller.{ArrivalRate, SimulationGenerator}

/**
  * Discrete Events that need to be handled.
  */
sealed trait DiscreteEvent extends Ordered[DiscreteEvent] {
  /** The timestamp of the event */
  def time: Long

  val classOrder: Short

  /** Events need to be ordered based on their timestamp. */
  def compare(that: DiscreteEvent): Int = {
    lazy val tOrder = this.time.compare(that.time)
    lazy val cOrder = this.classOrder.compare(that.classOrder)
    lazy val vOrder = sameClassCompare(that)

    if (tOrder != 0) tOrder
    else if (cOrder != 0) cOrder
    else if (vOrder != 0) vOrder
    else this.hashCode().compare(that.hashCode())
  }

  def sameClassCompare(that: DiscreteEvent): Int
}

/**
  * Event fired when a [[Task]] has finished.
  *
  * @param time The timestamp of the event
  * @param task The [[Task]] that was finished.
  */
case class FinishingTask(override val time: Long, task: Task) extends DiscreteEvent {
  override val classOrder: Short = 5

  override def sameClassCompare(that: DiscreteEvent): Int = that match {
    case FinishingTask(_, t) => task.compare(t)
    case _ => 0
  }
}

/**
  * Event fired when a simulation needs to start.
  *
  * @param time The timestamp of the event
  * @param simulation The actor reference to the [[Simulation]] that needs to start.
  */
case class StartingSim(override val time: Long, simulation: ActorRef) extends DiscreteEvent {
  override val classOrder: Short = 10

  override def sameClassCompare(that: DiscreteEvent): Int = that match {
    case StartingSim(_, s) => simulation.compareTo(s)
    case _ => 0
  }
}

/**
  * Event fired when a global time limit has been reached.
  *
  * @param time The timestamp of the event
  */
case class TimeLimit(override val time: Long) extends DiscreteEvent {
  override val classOrder: Short = 1

  override def sameClassCompare(that: DiscreteEvent): Int = 0
}


case class ArrivalProcess(override val time: Long, rate: ArrivalRate, simGenerator: SimulationGenerator) extends DiscreteEvent { //TODO simGenerator
  override val classOrder: Short = 7
  override def sameClassCompare(that: DiscreteEvent): Int = that match {
    //case ArrivalProcess(_, r) => rate.compareTo(r)
    case _ => 0
  }
}
