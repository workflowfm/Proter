package com.workflowfm.proter.controller

import akka.actor.{ActorRef, Props}
import com.workflowfm.proter.SingleTaskSimulation
import com.workflowfm.proter.ConstantGenerator
import com.workflowfm.proter.flows._
import com.workflowfm.proter.{TaskGenerator, ValueGenerator}

trait SimulationGenerator {
  def newSimProps(coordinator: ActorRef): Props
}

case class SingleTaskSimulationGenerator(resources: Seq[String], duration: ValueGenerator[Long]) extends SimulationGenerator {
    override def newSimProps(coordinator: ActorRef): Props = {
        //val r = new scala.util.Random()
        val name: String = "testSim" //r.nextString(10)
        SingleTaskSimulation.props(name,coordinator,resources,duration)
    }
}

case class FlowSimulationGenerator() extends SimulationGenerator {
  override def newSimProps(coordinator: ActorRef): Props = {
    val task1 = FlowTask(TaskGenerator("task1","flow",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r1")))
    val task2 = FlowTask(TaskGenerator("task2","flow",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r2")))
    val task3 = FlowTask(TaskGenerator("task3","flow",ConstantGenerator(1L),ConstantGenerator(0L)).withResources(Seq("r3")))

    val flow = task1 > task2 > task3
    FlowSimulationActor.props("flow",coordinator, flow)
  }
}
