package com.workflowfm.simulator.flows

import com.workflowfm.simulator._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import akka.actor.{ Actor, ActorRef, Props }
import java.util.UUID

sealed trait Flow {
  val id: UUID = java.util.UUID.randomUUID
  def +(f: Flow) = And(this, f)
  def >(f: Flow) = Then(this, f)
  def |(f: Flow) = Or(this, f)
}

case class NoTask() extends Flow
case class FlowTask(generator: TaskGenerator) extends Flow {override val id = generator.id}
case class Then(left: Flow, right: Flow) extends Flow
case class And(left: Flow, right: Flow) extends Flow
case class All(elements: Flow*) extends Flow
case class Or(left: Flow, right: Flow) extends Flow

/**
  * An actor which implements the "Flows" interface which can be used to create simple workflow simulations.
  *
  * This actor uses a [[Flow]] structure which describes how and in what order certain tasks should be executed.
  * A Flow may consist of a single FlowTask or some combination of FlowTasks which are joined using `And`s, `Then`s,
  * and `Or`s.
  *
  * @param name The name of the simulation being managed.
  * @param coordinator A reference to the [[Coordinator]] actor running the simulation.
  * @param flow The flow which describes how the simulation behaves
  * @param executionContext
  */
class FlowSimulationActor(
    name: String,
    coordinator: ActorRef,
    protected val flow: Flow
)(implicit executionContext: ExecutionContext)
    extends AsyncSimulation(name, coordinator)(executionContext) {

  /**
    * Initiates the execution of the simulation.
    *
    * @return A `Future` that completes with a custom output when the simulation is completed.
    */
  override def run(): Future[Any] = {
    val promise = Promise[Any]()
    runFlow(flow, ((_, _) => promise.success(Unit)))
    ready()
    promise.future
  }

  /**
    * Runs a `Flow` by either innitiating a FlowTask or decomposing a more complex flow via the `execute` method.
    * In both cases, the callback of the flow is stored in a map.
    *
    * @param flow The flow to run.
    * @param callback The callback function which is executed once this flow completes.
    */
  protected def runFlow(flow: Flow, callback: Callback): Unit = {
    flow match {
      case f: FlowTask =>
        task(f.generator, ((t, l) => { callback(t, l); ack(Seq(f.id)) }))
      case f: Flow => { tasks += flow.id -> callback; execute(f) }
    }
  }

  /**
    * Completes an id by executing its callback and then removing it from the map
    *
    * This overloads the definition of `complete` found in [[Simulation]] to allow for any id to be used in the
    * `tasks` map, and not just [[Task]] objects.
    *
    * @param id The id to complete
    */
  protected def complete(id: UUID) = {
    tasks.get(id).map(_(null, 0L))
    tasks -= id
  }

  /**
    * Executes a flow by translating from a flow object to its sub-parts and appropriate
    * callbacks, then calling `runFlow`.
    *
    * @param flow The flow to be executed
    */
  protected def execute(flow: Flow) {
    flow match {
      case f: NoTask => complete(f.id)

      case FlowTask(generator: TaskGenerator) => {}
      //this is here for the sake of case completeness, should not be called

      case f: Then => {
        val rightCallback: Callback = (_, _) => complete(f.id)
        val leftCallback: Callback = (_, _) => runFlow(f.right, rightCallback)
        runFlow(f.left, leftCallback)
      }

      case f: And => {
        val leftCallback: Callback = (_, _) => (if (!tasks.contains(f.right.id)) complete(f.id))
        val rightCallback: Callback = (_, _) => (if (!tasks.contains(f.left.id)) complete(f.id)) 
        runFlow(f.left, leftCallback)
        runFlow(f.right, rightCallback)
      }

      case f @ All(elem @ _*) =>
        runFlow((elem.fold(NoTask()) { (l, r) => And(l, r) }), (_, _) => complete(f.id))

      case f: Or => {
        val leftCallback: Callback = (_, _) => (if (tasks.contains(f.right.id)) complete(f.id))
        val rightCallback: Callback = (_, _) => (if (tasks.contains(f.left.id)) complete(f.id))
        runFlow(f.left, leftCallback)
        runFlow(f.right, rightCallback)
      }
    }
  }
}

object FlowSimulationActor {

  /**
    * Creates props of a [[FlowSimulationActor]].
    *
    * @param name The simulation name.
    * @param coordinator The [[Coordinator]].
    * @param flow The [[Flow]] to be executed
    * @param executionContext
    * @return The Props of a new flow simulation actor
    */
  def props(name: String, coordinator: ActorRef, flow: Flow)(
      implicit executionContext: ExecutionContext
  ): Props =
    Props(new FlowSimulationActor(name, coordinator, flow))
}

trait FlowsLookahead extends FlowSimulationActor with Lookahead {
  override def run(): Future[Any] = {
    parseFlow(flow)
    super.run()
  }

  private def parseFlow(flow: Flow, extraConditions: Set[UUID] = Set()): Set[UUID] = {
    flow match {
      case f: NoTask => Set()
      case FlowTask(g) => Set(g.id)
      case f: Then => {
        val l = parseFlow(f.left,extraConditions)
        f.right match { case FlowTask(g) => lookahead = lookahead + (l++extraConditions,g); case _ => Unit}
        f.left  match { case t:FlowTask => parseFlow(f.right,extraConditions+t.id); case _ => parseFlow(f.right,extraConditions)}
      }
      case f: And => parseFlow(f.left,extraConditions) ++ parseFlow(f.right,extraConditions)
      case f @ All(elem @ _*) => elem.foldLeft(Set.empty[UUID]){ (a,b) => a ++ parseFlow(b,extraConditions) }
      case f: Or => {
        parseFlow(f.left,extraConditions) ++ parseFlow(f.right,extraConditions)
      }
    }
    Set()
  }
}