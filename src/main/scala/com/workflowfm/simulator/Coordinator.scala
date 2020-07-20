package com.workflowfm.simulator

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import com.workflowfm.simulator.events._
import scala.concurrent.duration._
import com.workflowfm.simulator.metrics._
import scala.collection.mutable.{ Map, Queue, PriorityQueue, HashSet, SortedSet }
import scala.concurrent.{ Promise, Await, ExecutionContext }
import scala.util.{ Failure, Success, Try }
import java.util.UUID
import uk.ac.ed.inf.ppapapan.subakka.HashSetPublisher

/**
  * Provides coordination for discrete event simulation of multiple asynchronous simulations.
  *
  * @param scheduler The [[Scheduler]] responsible for task allocation at any given time.
  * @param startingTime The starting timestamp of the entire simulation. 
  */
class Coordinator(
  scheduler :Scheduler,
  startingTime:Long
) extends HashSetPublisher[Event] {
 
  /** Discrete Events that need to be handled. */
  sealed trait CEvent extends Ordered[CEvent] {
    /** The timestamp of the event  */
    def time: Long

    /** Events need to be ordered based on their timestamp. */
    def compare(that:CEvent) = {
      that.time.compare(time)
    }
  }

  /** 
    * Event fired when a [[Task]] has finished.
    * @param time The timestamp of the event
    * @param task The [[Task]] that was finished.
    */ 
  case class FinishingTask(override val time: Long, task: Task) extends CEvent

  /** 
    * Event fired when a simulation needs to start.
    * @param time The timestamp of the event
    * @param simulation The actor reference to the [[SimulationActor]] that needs to start.
    */ 
  case class StartingSim(override val time: Long, simulation: ActorRef) extends CEvent
  
  /** Map of the available [[TaskResource]]s */
  val resourceMap: Map[String,TaskResource] = Map[String,TaskResource]()

  /** Set of [[SimulationActor]] references that we need to wait for before we can progress time. */
  val waiting: HashSet[ActorRef] = HashSet[ActorRef]()
  val waitingForTask: HashSet[UUID] = HashSet[UUID]()

  /** Set of simulation names that are running, i.e. they have already started but not finished. */
  val simulations: HashSet[String] = HashSet[String]()

  /** A sorted queue of tasks that need to be run. */
  val tasks: SortedSet[Task] = SortedSet()
  
  /** [[scala.collection.mutable.PriorityQueue]] of discrete [[CEvent]]s to be processed, ordered by (future) timestamp. */
  val events = new PriorityQueue[CEvent]()
  
  /** The current virtual time. */
  var time = startingTime
 
  /** 
    * Add a new [[TaskResource]] the our map of available resources. 
    * @param r The [[TaskResource]] to be added.
    */ 
  def addResource(r: TaskResource) = if (!resourceMap.contains(r.name)) {
    publish(EResourceAdd(self, time, r.name, r.costPerTick))
    resourceMap += r.name -> r
  }

  /** 
    * Extract all [[CEvent]]s in the queue that need to be processed in a given timestamp.
    * Sequentially builds a [[scala.collection.immutable.Seq]].
    * At the same time it dequeues the events from the event queue.
    * @param t The given timestamp to look out for. We assume it is less than or equal to
    *          the timestamp of the first [[CEvent]] in the queue.
    */ 
  protected def dequeueEvents(t: Long): Seq[CEvent] = {
    val elems = scala.collection.immutable.Seq.newBuilder[CEvent]
    while(events.headOption.exists(_.time == t)) {
      elems += events.dequeue
    }
    elems.result()
  }

  /** 
    * Progress virtual time by processing the next [[CEvent]] in the queue.
    * This is called when we are done waiting for any simulations to respond.
    * We take the first event from the queue and then use [[dequeueEvents]] to get
    * all events with the same timestamp. 
    * 
    * First, [[releaseResources]] releases all resources that are no longer in use. 
    * It is useful to do this before notifying task completion to ensure all simulations can know the 
    * correct state of the resources if they need to.
    * 
    * We then handle the events with [[handleCEvent]].
    * If there are no events, no tasks to run, and all simulations have finished, the whole
    * simulation is done, so we publish [[com.workflowfm.simulator.events.EDone]].
    * If there are no events and no simulations to wait for, but there are still tasks to run, we
    * attempt to allocate and run them. This may happen if something breaks when handling a previous
    * event. 
    */
  protected def tick(): Unit = {
    // Are events pending?
    if (!events.isEmpty) {
      // Grab the first event
      val firstEvent = events.head

      // Did we somehow go past the event time? This should never happen.
      if (firstEvent.time < time) {
        publish(EError(self, time, s"Unable to handle past event for time: [${firstEvent.time}]"))
      } else {
        // Jump ahead to the event time. This is a priority queue so we shouldn't skip any events
    	time = firstEvent.time

        // Dequeue all the events that need to happen now
        val eventsToHandle = dequeueEvents(firstEvent.time)

        // Release all resources from finished tasks before you notify anyone
        eventsToHandle foreach releaseResources
        // Handle the event
        eventsToHandle foreach handleCEvent
      }

    }
    else if (tasks.isEmpty && simulations.isEmpty) {
      publish(EDone(self, time))

    } else if (waitingForTask.isEmpty && !tasks.isEmpty) { // this may happen if handleCEvent fails
      allocateTasks()
      tick()
    } //else {
      //publish(EError(self, time, "No tasks left to run, but simulations have not finished."))
    //}
  }

  /**
    * Allocates the tasks due to start next to their resources.
    * Asks the [[Scheduler]] to determine what tasks need to start next.
    * Removes each of them from the queue and runs them using [[startTask]].
    */
  protected def allocateTasks() = {
    // Assign the next tasks
    scheduler.getNextTasks(tasks, time, resourceMap).foreach { task =>
      tasks -= task
      startTask(task)
    }
  }

/**
  * Releases any resources that are not longer used based on the given event.
  * If the provided event is a [[FinishingTask]] event, i.e. a [[Task]] just finished,
  * this means the resources used by that [[Task]] can now be released.
  * Other [[CEvent]]s are just ignored.
  * 
  * @param event The [[CEvent]] that potentially released resources.
  * @return
  */
  protected def releaseResources(event: CEvent) = {
    event match {
      case FinishingTask(t,task) if (t == time) =>
        // Unbind the resources
        task.taskResources(resourceMap).foreach(detach)
      case _ => Unit
    }
  }

/**
  * Processes a [[CEvent]].
  * 
  * - [[FinishingTask]] means a task finished and we need to stop it with [[stopTask]].
  * 
  * - [[StartingSim]] means a simulation needs to start and we do this with [[startSimulationActor]].
  *
  * @param event The [[CEvent]] to process.
  */
  protected def handleCEvent(event:CEvent) = {
    log.debug(s"[COORD:$time] Event!")
    event match {
      // A task is finished
      case FinishingTask(t,task) if (t == time) => stopTask(task)

      // A simulation (workflow) is starting now
      case StartingSim(t, sim) if (t == time)=> startSimulationActor(sim)

      case _ => publish(EError(self, time, s"Failed to handle event: $event"))
    }
  }

/**
  * Add a new simulation to be run.
  *
  * @param t The timestamp when the simulation needs to start. Must be greater or equal to the current
  *          time.
  * @param actor The reference to the [[SimulationActor]] corresponding to the simulation.
  * @return
  */
  protected def addSimulation(t: Long, actor: ActorRef) = {
    publish(ESimAdd(self, time,actor.toString(),t))
    if (t >= time) events += StartingSim(t, actor)
  }

/**
  * Adds multiple simulations at the same time.
  * This is equivalent to mapping [[addSimulation]] over the given sequence, but more efficient.
  * 
  * @param sims A sequence of pairs, each consisting of a starting timestamp and a 
  * reference to a [[SimulationActor]]. Timestamps must be greater or equal to the current time.
  * @return
  */
  protected def addSimulations(sims: Seq[(Long,ActorRef)]) = {
    events ++= sims.flatMap { case(t, actor) => {
      publish(ESimAdd(self, time,actor.toString(),t)) }
      if (t >= time)
        Some(StartingSim(t, actor))
      else
        None
    }
  }

/**
  * Start a simulation via the reference to its [[SimulationActor]].
  * Once the simulation starts, we exect to hear from it in case it wants to add some [[Task]]s.
  * We therefore add it to the waiting queue.
  *
  * @param simActor The [[akka.actor.ActorRef]] of the [[SimulationActor]].
  */
  protected def startSimulationActor(simActor: ActorRef) = {
    waiting += simActor
    simActor ! SimulationActor.Start
  }


/**
  * Wrapper of [[startSimulationActor]] that also publishes a [[com.workflowfm.simulator.events.ESimStart]].
  *
  * @param name The name of the simulation.
  * @param simActor The [[akka.actor.ActorRef]] of the corresponding [[SimulationActor]].
  */
  protected def startSimulation(name: String, simActor: ActorRef): Unit = {
    publish(ESimStart(self, time,name))
    simulations += name
  }

/**
  * Stops a simulation when it is done.
  * Removes the simulation from the list of running simulations.
  * Publishes a [[com.workflowfm.simulator.events.ESimEnd]].
  * Calls [[ready]] to handle the fact that we no longer need to wait for this simulation.
  *
  * @param name The name of the completed simulation.
  * @param result A string representation of the output of the simulation.
  * @param actor The [[akka.actor.ActorRef]] of the corresponding [[SimulationActor]].
  */
  protected def stopSimulation(name: String, result: String, actor: ActorRef) = {
    simulations -= name
    publish(ESimEnd(self, time,name,result))
    log.debug(s"[COORD:$time] Finished: [${actor.path.name}]")
    ready(actor,Seq.empty[UUID])
  }
  
/**
  * Adds new [[Task]]s for a simulation.
  * Calls [[addTask]] for each [[Task]] to be generated.
  * Calls [[ready]] to handle the fact that we no longer need to wait for this simulation. 
  *
  * @param actor The [[akka.actor.ActorRef]] of the [[SimulationActor]] that needs to generate the tasks.
  * @param l The list of tasks to be generated, each represented by a triplet with its unique ID, 
  *          [[TaskGenerator]] and list of [[TaskResource]] names that need to be used.
  */
  protected def addTasks(actor: ActorRef, l: Seq[(UUID, TaskGenerator, Seq[String])], ack: Seq[UUID]) { //TODO update scaladoc
    l map { case (i,g,r) => addTask(i,g,r) }
    log.debug(s"[COORD:$time] Ready: [${actor.path.name}]")
    ready(actor,ack)
  }

/**
  * Adds a single new [[Task]].
  * 
  * - Uses [[TaskGenerator.create]] to create the [[Task]], which will now have a fixed duration and cost.
  * 
  * - Uses the current time as the creation time, unless the [[TaskGenerator]] has a custom creation time.
  * 
  * - Calculates the cost of the involved resources by adding the [[TaskResource.costPerTick]] 
  * multipled by the [[Task.duration]]. Adds this to the [[Task.cost]].
  * 
  * - Publishes a [[com.workflowfm.simulator.events.ETaskAdd]].
  * 
  * - If the task does not require any resources, it is started immediately using [[startTask]].
  * Otherwise, we add it to the queue of [[Task]]s.
  *
  * @param id A unique ID for the [[Task]].
  * @param gen The [[TaskGenerator]] that will generate the [[Task]].
  * @param resources The list of [[TaskResource]] names that need to be used by the [[Task]].
  */
  protected def addTask(id: UUID, gen: TaskGenerator, resources: Seq[String]) {
    val creation = if (gen.createTime >= 0) gen.createTime else time
    // Create the task
    val t: Task = gen.create(id, creation, sender, resources:_*)
    
    // Calculate the cost of all resource usage. We only know this now!
    val resourceCost = (0L /: t.taskResources(resourceMap)) { case (c,r) => c + r.costPerTick * t.duration }
    t.addCost(resourceCost)

    publish(ETaskAdd(self, time,t))

    if (resources.length > 0)
      tasks += t
    else
      // if the task does not require resources, start it now
      startTask(t)

    //sender() ! Coordinator.AckTask(t) //uncomment this to acknowledge AddTask
  }

/**
  * Start a [[Task]] at the current timestamp.
  * A [[Task]] is started when scheduled, meaning all the [[TaskResource]]s it needs are available
  * and it is the highest priority [[Task]] in the queue for those [[TaskResource]]s.
  * 
  * - Publishes a [[com.workflowfm.simulator.events.ETaskAdd]].
  * 
  * - Calls [[TaskResource.startTask]] for each involved [[TaskResource]] to attach this [[Task]] 
  * to them. Publishes a [[com.workflowfm.simulator.events.ETaskAttach]] for each successful attachment.
  * Otherwise publishes an appropriate [[com.workflowfm.simulator.events.EError]]. The latter would
  * only happen if the [[Scheduler]] tried to schedule a [[Task]] to a busy [[TaskResource]].
  * 
  * - Creates a [[FinishingTask]] event for this [[Task]] based on its duration, and adds it to 
  * the even queue.
  *
  * @param task The [[Task]] to be started.
  */
  protected def startTask(task: Task) {
    publish(ETaskStart(self, time,task))
    // Mark the start of the task in the metrics
    task.taskResources(resourceMap) map { r =>
      // Bind each resource to this task
      r.startTask(task, time) match {
        case None =>
          publish(ETaskAttach(self, time,task,r.name))
        case Some(other) =>
          publish(EError(self, time,s"Tried to attach task [${task.name}](${task.simulation}) to [${r.name}], but it was already attached to [${other.name}](${other.simulation}) "))
      }
    }
    // Generate a FinishTask event to be triggered at the end of the event
    events += FinishingTask(time+task.duration,task)
  }

/**
  * Adds a simulation to the waiting list.
  * We will wait for that simulation to send tasks or finish before we progress time.
  * This is used when a simulation wants to react externally to events from another simulation.
  * 
  * Other events that warrant waiting for a simulation to react do not call this method, but
  * add the simulation to the waiting list on their own.
  *
  * @param actor The reference to the [[SimulationActor]] we need to wait for.
  */
  protected def waitFor(actor: ActorRef) {
    waiting += actor
    log.debug(s"[COORD:$time] Wait requested: ${actor.path.name}")
    actor ! Coordinator.AckWait
  }

/**
  * Detaches the task attached to the given [[TaskResource]]. 
  * A wrapper of [[TaskResource.finishTask]] that 
  * publishes a [[com.workflowfm.simulator.events.ETaskDetach]].
  *
  * @param r The [[TaskResource]] to free up.
  */
  protected def detach(r: TaskResource) = {
    r.finishTask(time) match {
      case None => Unit
      case Some(task) => publish(ETaskDetach(self, time,task,r.name))
    }
  }

  /**
    * Handles a [[Task]] that has just finished.
    * - Adds the corresponding [[SimulationActor]] reference to the waiting list as we
    * expect it to react to the task finishing.
    * 
    * - Publishes a [[com.workflowfm.simulator.events.ETaskDone]].
    * 
    * - Notifies the [[SimulationActor]] that its [[Task]] has finished.
    * 
    * Note that resources are detached before this in [[tick]] using [[releaseResources]].
    *
    * @param task The [[Task]] that needs to be stopped.
    */
  protected def stopTask(task: Task) {
    waiting += task.actor
    waitingForTask += task.id
    log.debug(s"[COORD:$time] Waiting post-task: ${task.actor.path.name}")
    publish(ETaskDone(self, time,task))
    task.actor ! SimulationActor.TaskCompleted(task, time)
  }

/**
  * Reacts to the fact that we no longer need to wait for a simulation.
  * This can happen when the simulation has finished or has added its new tasks. 
  * We remove it from the waiting queue and then check if the waiting queue is empty.
  * If it is, we can progress time. First, we allocate new tasks, because some of them
  * may be able to start immediately. We then progress time with [[tick]].
  *
  * @param actor The [[akka.actor.ActorRef]] of the [[SimulationActor]] that is ready.
  */
  protected def ready(actor: ActorRef, ack: Seq[UUID]): Unit = {
    waiting -= actor
    ack map {x=> waitingForTask -= x}
    log.debug(s"[COORD:$time] Waiting: ${waiting map (_.path.name)}")
    // Are all actors ready?
    if (waitingForTask.isEmpty) {
      allocateTasks()
      tick()
    }
  }

/**
  * Starts the entire simulation.
  */
  def start() = {
    publish(EStart(self))
    tick()
  }

/**
  * Checks if a given [[com.workflowfm.simulator.events.Event]] in the output stream is the final one.
  * Causes the stream to shutdown after [[com.workflowfm.simulator.events.EDone]] is published.
  *
  * @param e The [[com.workflowfm.simulator.events.Event]] to check.
  * @return true if it is a [[com.workflowfm.simulator.events.EDone]], otherwise false.
  */
  override def isFinalEvent(e: Event) = e match {
    case EDone(_,_) => true
    case _ => false
  }

/**
  * Defines the [[Receive]] behaviour related to the coordination of the simulations.
  *
  * @return The [[Receive]] behaviour.
  */
  def receiveBehaviour: Receive = {
    case Coordinator.AddSim(t, s) => addSimulation(t,s)
    case Coordinator.AddSims(l) => addSimulations(l)
    case Coordinator.AddSimNow(s) => addSimulation(time, s)
    case Coordinator.AddSimsNow(l) => addSimulations(l.map((time,_)))

    case Coordinator.AddResource(r) => addResource(r)
    case Coordinator.AddResources(r) => r foreach addResource
      
    case Coordinator.AddAndAckTasks(l,ack) => addTasks(sender, l, ack)
    case Coordinator.WaitFor(actor) => waitFor(actor)
    case Coordinator.SimStarted(name) => startSimulation(name, sender)
    case Coordinator.SimDone(name, result) => result match {
      case Success(res) => {
        stopSimulation(name, res.toString, sender)
      }
      case Failure(ex) => {
        stopSimulation(name, ex.getLocalizedMessage, sender)
        ex.printStackTrace()
      }
    }
    case Coordinator.Start => start()
    case Coordinator.Ping => sender() ! Coordinator.Time(time)
  }

/**
  * Defines the complete [[Receive]] behaviour of the actor as a publisher and coordinator.
  *
  * @return The [[Receive]] behaviour.
  */
  override def receive = LoggingReceive { publisherBehaviour orElse receiveBehaviour }
}

object Coordinator {
  case object Start
  case object Ping
  case class Time(time: Long)

  case class AddSim(t: Long, actor: ActorRef)
  case class AddSims(l: Seq[(Long,ActorRef)])
  case class AddSimNow(actor: ActorRef)
  case class AddSimsNow(l: Seq[ActorRef])

  case class AddResource(r: TaskResource)
  case class AddResources(l: Seq[TaskResource])
  
  case class SimStarted(name: String)
  case class SimDone(name: String, result: Try[Any])

  case class AddAndAckTasks(l: Seq[(UUID, TaskGenerator, Seq[String])], ack: Seq[UUID])
  case object AckTask
  case class WaitFor(actor: ActorRef)
  case object AckWait

  def props(
    scheduler: Scheduler,
    startingTime: Long = 0L
  )(implicit system: ActorSystem, executionContext:ExecutionContext
  ): Props = Props(new Coordinator(scheduler,startingTime))
}
