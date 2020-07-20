package com.workflowfm.simulator.events

import akka.actor.{ ActorRef, ActorSystem }
import java.text.SimpleDateFormat
import scala.collection.mutable.HashSet
import scala.concurrent.Promise
import uk.ac.ed.inf.ppapapan.subakka.Subscriber

/**
  * A [[uk.ac.ed.inf.ppapapan.subakka.Subscriber]] for [[Coordinator]] [[Event]]s.
  */
trait EventHandler extends Subscriber[Event]

/**
  * An [[EventHandler]] for a pool of [[Coordinator]]s.
  */
trait PoolEventHandler extends EventHandler {
  /**
    * The set of [[Coordinator]]s we have subscribed to.
    */
  val coordinators: HashSet[ActorRef] = HashSet[ActorRef]()

  /**
    * Initializes the event stream by adding the coordinator to [[coordinators]].
    *
    * @param a The reference to the [[Coordinator]] actor that initialized the stream.
    */
  override def onInit(a: ActorRef) = {
    println(s"Pool handler adding coordinator: $a")
    coordinators += a
  }

  /** 
    * Closes the event stream by removing the coordinator from [[coordinators]].
    *
    * @param a The reference to the [[Coordinator]] that closed the stream.
    * @param s 
    */
  override def onDone(a: ActorRef, s: ActorRef) = {
    println(s"Pool handler done with coordinator: $a")
    coordinators -= a
  }
}

/**
  * An [[EventHandler]] that prints events to standard error.
  *
  */
class PrintEventHandler extends EventHandler {   
  val formatter = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS")
  override def onEvent(e: Event) = {
    val time = formatter.format(System.currentTimeMillis())
    System.err.println(s"[$time] ${Event.asString(e)}")
  }
}

/**
  * An [[EventHandler]] with a measured result.
  * @tparam R The type of the result.
  */
trait ResultHandler[R] extends EventHandler {   
  def result: R
}

/**
  * A [[ResultHandler]] that counts the events seen.
  *
  */
class CounterHandler extends ResultHandler[Int] {
  var count = 0

  override def onInit(a: ActorRef) = count = 0
  override def onEvent(e: Event) = count = count + 1
  override def result = count
}

/**
  * A wrapper for a [[ResultHandler]] that fulfills a [[scala.concurrent.Promise]] with the result.
  * This is also itself a [[ResultHandler]].
  *
  * @param handler The [[ResultHandler]] that calculates the result.
  */
class PromiseHandler[R](handler: ResultHandler[R]) extends ResultHandler[R] {
  protected val promise = Promise[R]()
  def future = promise.future

  override def onInit(a: ActorRef): Unit = handler.onInit(a)
  override def onEvent(e: Event): Unit = handler.onEvent(e)

  override def onDone(a: ActorRef, s: ActorRef): Unit = {
    handler.onDone(a,s)
    if (!promise.isCompleted) promise.success(result)
  }

  override def onFail(ex: Throwable, a: ActorRef, s: ActorRef) = {
    handler.onFail(ex, a, s)
    if (!promise.isCompleted) promise.failure(ex)
  }

  override def result = handler.result
}
object PromiseHandler {
  def of[R](handler: ResultHandler[R]): PromiseHandler[R] = new PromiseHandler[R](handler)
}

/**
  * A [[PoolEventHandler]] that shuts down the [[akka.actor.ActorSystem]] when all [[Coordinator]]s are done.
  *
  * @param system The [[akka.actor.ActorSystem]] to shut down.
  */
class ShutdownHandler(implicit system: ActorSystem) extends PoolEventHandler {
  override def onDone(a: ActorRef, s: ActorRef): Unit = {
    super.onDone(a,s)
    if (coordinators.isEmpty) {
      println("********************************************* SHUTTING DOWN!!! ***********************************************")
      Thread.sleep(1000)
      system.terminate()
    }
  }
}
