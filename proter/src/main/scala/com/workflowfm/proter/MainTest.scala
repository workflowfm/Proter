package com.workflowfm.proter

import com.workflowfm.proter._
import com.workflowfm.proter.flows._
import com.workflowfm.proter.metrics._
import com.workflowfm.proter.schedule._
import java.io.File
import scala.concurrent.duration._
import scala.concurrent.Await


object MainTest {
    def main(args: Array[String]): Unit = {
        //implicit val context: ExecutionContext = ExecutionContext.global

        val coordinator = new Coordinator(new ProterScheduler())

        val handler = SimMetricsOutputs(
        new SimMetricsPrinter(),
        new SimCSVFileOutput("output" + File.separator,"MainTest"),
        new SimD3Timeline("output" + File.separator,"MainTest")
        )

        // Subscribe the metrics actor
        coordinator.subscribe(new SimMetricsHandler(handler))


        val r1 = new TaskResource("r1",0)
        val r2 = new TaskResource("r2",0)
        val r3 = new TaskResource("r3",0)
        val r4 = new TaskResource("r4",0)
        val r5 = new TaskResource("r5",0)
      
        val resources = Seq(r1,r2,r3,r4,r5)
        coordinator.addResources(resources)

        // val task1 = new FlowTask(Task("task1",2L).withResources(Seq("r1")).withPriority(Task.High))
        // val task2 = new FlowTask(Task("task2",3L).withResources(Seq("r3")).withPriority(Task.Medium))
        // val task3 = new FlowTask(Task("task3",4L).withResources(Seq("r2")).withPriority(Task.High))

        // val flow = (task1 > task2) > task3
        //coordinator.addArrivalNow(10, Constant(10L), new FlowSimulationGenerator("flow", flow))

        val flow = new RandomFlowFactory(0.5f, resources).withTasks(Uniform(1, 10)).withDurations(Uniform(1,10)).withNumResources(Uniform(1,3)).newFlow
        print(flow)
        val flow_sim = new FlowSimulation("sim",coordinator,flow)
        coordinator.addSimulationNow(flow_sim)

         Await.result(coordinator.start(), 1.hour)

        
  }  
}
