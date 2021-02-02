package com.workflowfm.proter

import com.workflowfm.proter._
import com.workflowfm.proter.flows._
import com.workflowfm.proter.controller.Controller
import akka.actor.{ActorSystem,Props}
import com.workflowfm.proter.controller.NegativeExponentialRate


object MainTest {

    def main(args: Array[String]): Unit = {
        implicit val system: ActorSystem = ActorSystem("Controller")
        val coordinator = system.actorOf(Coordinator.props(new DefaultScheduler()))

        val r1 = new TaskResource("r1",0)
        val r2 = new TaskResource("r2",0)
        val r3 = new TaskResource("r3",0)
        // val r4 = new TaskResource("r4",0)
        // val r5 = new TaskResource("r5",0)
        // val r6 = new TaskResource("r6",0)
        // val r7 = new TaskResource("r7",0)
        // val r8 = new TaskResource("r8",0)

        val task1 = FlowTask(TaskGenerator("task1","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r1")))
        val task2 = FlowTask(TaskGenerator("task2","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r2")))
        val task3 = FlowTask(TaskGenerator("task3","sim1",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r3")))

        val flow = task1 > task2 > task3
        
        Controller.addResource(r1)
        Controller.addResource(r2)
        Controller.addResource(r3)

        //Controller.addSim(FlowSimulationActor.props("sim1"))

        println("ARRIVAL RATES TEST")
        val p = NegativeExponentialRate(0.5)
        var x: Double = 0
        for ( i <- 1 to 10) {
            x = p.next(x)
            println(x)
        }

        Controller.run()

        //val idk1 = system.actorOf( Props(new FlowLookaheadActor("sim1", coordinator, flow)), "sim1")
        //val testboi = new FlowLookaheadActor("sim2",coordinator,flow)
        //val idk2 = system.actorOf( Props(testboi) , "sim2")
    }

}