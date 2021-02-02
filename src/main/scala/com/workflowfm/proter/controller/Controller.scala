package com.workflowfm.proter.controller

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import com.workflowfm.proter._
import com.workflowfm.proter.flows._
import com.workflowfm.proter.metrics._
import com.workflowfm.proter.events.{ ShutdownHandler }
import uk.ac.ed.inf.ppapapan.subakka.Subscriber
import java.io.File
import akka.actor.Props

object Controller {
    var sims: scala.collection.mutable.Set[(Props,String)] = scala.collection.mutable.Set()
    var resources: scala.collection.mutable.Set[TaskResource] = scala.collection.mutable.Set()

    def addSim(sim: Props, name: String): Unit = {
        sims += ((sim, name))
    }

    def addResource(resource: TaskResource): Unit = {
        resources += resource
    }

    def run(): Unit = {

        implicit val system: ActorSystem = ActorSystem("Controller")
        implicit val executionContext: ExecutionContext = ExecutionContext.global
        implicit val timeout = Timeout(2.seconds)

        val coordinator = system.actorOf(Coordinator.props(new DefaultScheduler()))
        val shutdownActor = Subscriber.actor(new ShutdownHandler())

        val handler = SimMetricsOutputs(
	        new SimMetricsPrinter(),
            new SimCSVFileOutput("output" + File.separator,"Controller"),
	        new SimD3Timeline("output" + File.separator,"Controller")
        )

        Await.result(new SimOutputHandler(handler).subAndForgetTo(coordinator,Some("MetricsHandler")), 3.seconds)
        Await.result(shutdownActor ? Subscriber.SubAndForgetTo(coordinator), 3.seconds)

        val DEBUG = false
        if (DEBUG) {
            println(s"Cores: ${Runtime.getRuntime().availableProcessors()}")
            val config = system.settings.config.getConfig("akka.actor.default-dispatcher")
            println(s"Parallelism: ${config.getInt("fork-join-executor.parallelism-min")}-${config.getInt("fork-join-executor.parallelism-max")} x ${config.getDouble("fork-join-executor.parallelism-factor")}")
            val printer = new com.workflowfm.proter.events.PrintEventHandler
            Await.result(printer.subAndForgetTo(coordinator), 3.seconds)
        }

        //=========================================================================================

        // // Define resources
        val rList = resources.toList
        coordinator ! Coordinator.AddResources(rList)

        //TEMP
        addSim(FlowSimulationActor.props("sim1",coordinator,flow),"sim1")
        addSim(FlowSimulationActor.props("sim2",coordinator,flow),"sim2")
        //addSim(FlowSimulationActor.props("sim3",coordinator,flow),"sim3")
        //addSim(SingleTaskSimulation.props("singleSim1",coordinator,Seq("r1"),ConstantGenerator(1L)), "singleSim1")
        //addSim(SingleTaskSimulation.props("singleSim2",coordinator,Seq("r1"),ConstantGenerator(1L)), "singleSim2")

        sims map { x=> coordinator ! Coordinator.AddSim(0L,system.actorOf( x._1 ,x._2)) }

        coordinator ! Coordinator.Start
    }

    //TEMP
    val task1 = FlowTask(TaskGenerator("task1","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r1")))
    val task2 = FlowTask(TaskGenerator("task2","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r2")))
    val task3 = FlowTask(TaskGenerator("task3","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r3")))

    val flow = task1 > task2 > task3
}