package com.workflowfm.simulator.examples

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import com.workflowfm.simulator._
import com.workflowfm.simulator.metrics._
import com.workflowfm.simulator.events.{ ShutdownHandler }
import com.workflowfm.simulator.flows._
import uk.ac.ed.inf.ppapapan.subakka.Subscriber
import java.io.File

object Example {
    //toggle for debug
    val DEBUG = false

    def main(args: Array[String]): Unit = {

        implicit val system: ActorSystem = ActorSystem("ExampleCode")
        implicit val executionContext: ExecutionContext = ExecutionContext.global
        implicit val timeout = Timeout(2.seconds)

        val coordinator = system.actorOf(Coordinator.props(new LookaheadScheduler()))
        val shutdownActor = Subscriber.actor(new ShutdownHandler())

        val handler = SimMetricsOutputs(
	        new SimMetricsPrinter(),
            new SimCSVFileOutput("output" + File.separator,"example"),
	        new SimD3Timeline("output" + File.separator,"example")
        )

        Await.result(new SimOutputHandler(handler).subAndForgetTo(coordinator,Some("MetricsHandler")), 3.seconds)
        Await.result(shutdownActor ? Subscriber.SubAndForgetTo(coordinator), 3.seconds)

        if (DEBUG) {
            println(s"Cores: ${Runtime.getRuntime().availableProcessors()}")
            val config = system.settings.config.getConfig("akka.actor.default-dispatcher")
            println(s"Parallelism: ${config.getInt("fork-join-executor.parallelism-min")}-${config.getInt("fork-join-executor.parallelism-max")} x ${config.getDouble("fork-join-executor.parallelism-factor")}")
            val printer = new com.workflowfm.simulator.events.PrintEventHandler
            Await.result(printer.subAndForgetTo(coordinator), 3.seconds)
        }

        //=========================================================================================

        // Define resources
        val r1 = new TaskResource("r1",0)
        val r2 = new TaskResource("r2",0)
        val r3 = new TaskResource("r3",0)
        val r4 = new TaskResource("r4",0)
        val r5 = new TaskResource("r5",0)
        val r6 = new TaskResource("r6",0)
        val r7 = new TaskResource("r7",0)
        val r8 = new TaskResource("r8",0)
        val resources = List (r1,r2,r3,r4,r5,r6,r7,r8)
        coordinator ! Coordinator.AddResources(resources)

        {
            val task1 = FlowTask(TaskGenerator("task1", "sim", ConstantGenerator(4L), ConstantGenerator(0L)) withResources(Seq("r2")))
            val task2 = FlowTask(TaskGenerator("task2", "sim", ConstantGenerator(4L), ConstantGenerator(0L)) withResources(Seq("r1")))
            val flow = task1 > task2

            coordinator ! Coordinator.AddSim(0L,system.actorOf(FlowLookaheadActor.props("sim",coordinator,flow)))
           
        }

        {
            val task1 = FlowTask(TaskGenerator("task1", "sim2", ConstantGenerator(4L), ConstantGenerator(0L)) withResources(Seq("r2")))
            val task2 = FlowTask(TaskGenerator("task2", "sim2", ConstantGenerator(4L), ConstantGenerator(0L)) withResources(Seq("r1")))
            val flow = task1 + task2

            coordinator ! Coordinator.AddSim(0L,system.actorOf(FlowLookaheadActor.props("sim2",coordinator,flow)))
            //todo A>B C+D example with renaming
            
        }

        {
            val task1 = FlowTask(TaskGenerator("task1", "sim3", ConstantGenerator(4L), ConstantGenerator(0L)) withResources(Seq("r2")))
            val task2 = FlowTask(TaskGenerator("task2", "sim3", ConstantGenerator(4L), ConstantGenerator(0L)) withResources(Seq("r1")))
            val flow = task1 | task2

            coordinator ! Coordinator.AddSim(0L,system.actorOf(FlowLookaheadActor.props("sim3",coordinator,flow)))
            
        }

        coordinator ! Coordinator.Start
    }
}