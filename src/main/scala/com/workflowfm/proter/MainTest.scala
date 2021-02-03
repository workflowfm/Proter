package com.workflowfm.proter

import com.workflowfm.proter._
import com.workflowfm.proter.flows._
import com.workflowfm.proter.controller.Controller
import akka.actor.{ActorSystem,Props}
//import com.workflowfm.proter.controller.NegativeExponentialRate

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import com.workflowfm.proter._
import com.workflowfm.proter.flows._
import com.workflowfm.proter.metrics._
import com.workflowfm.proter.controller._
import com.workflowfm.proter.events.{ ShutdownHandler }
import uk.ac.ed.inf.ppapapan.subakka.Subscriber
import java.io.File
import akka.actor.Props


object MainTest {

    def main(args: Array[String]): Unit = {        
        // Controller.addResource(r1)
        // Controller.addResource(r2)
        // Controller.addResource(r3)

        //Controller.addSim(FlowSimulationActor.props("sim1"))

        // println("ARRIVAL RATES TEST")
        // val p = NegativeExponentialRate(0.5)
        // var x: Double = 0
        // for ( i <- 1 to 10) {
        //     x = p.next(x)
        //     println(x)
        // }

        // Controller.run()

        //val idk1 = system.actorOf( Props(new FlowLookaheadActor("sim1", coordinator, flow)), "sim1")
        //val testboi = new FlowLookaheadActor("sim2",coordinator,flow)
        //val idk2 = system.actorOf( Props(testboi) , "sim2")

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
        val r1 = new TaskResource("r1",0)
        val r2 = new TaskResource("r2",0)
        val r3 = new TaskResource("r3",0)
        // val r4 = new TaskResource("r4",0)
        // val r5 = new TaskResource("r5",0)
        // val r6 = new TaskResource("r6",0)
        // val r7 = new TaskResource("r7",0)
        // val r8 = new TaskResource("r8",0)
        val rList = Seq(r1,r2,r3)
        coordinator ! Coordinator.AddResources(rList)

        val task1 = FlowTask(TaskGenerator("task1","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r1")))
        val task2 = FlowTask(TaskGenerator("task2","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r2")))
        val task3 = FlowTask(TaskGenerator("task3","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r3")))

        val flow = task1 > task2 > task3

        //TEMP
        val sim = FlowSimulationActor.props("sim1",coordinator,flow)

        //coordinator ! Coordinator.AddSim(110L,system.actorOf(sim))

        coordinator ! Coordinator.LimitTime(100)
        coordinator ! Coordinator.AddArrivalProcessNow(NegativeExponentialRate(0.1),SingleTaskSimulationGenerator(Seq("r1","r2"),ConstantGenerator(2L)))
        //coordinator ! Coordinator.AddArrivalProcessNow(ConstantRate(2L),FlowSimulationGenerator())

        coordinator ! Coordinator.Start
    }

}