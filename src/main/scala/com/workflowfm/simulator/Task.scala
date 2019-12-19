package com.workflowfm.simulator

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Promise
import scala.concurrent.duration._
import com.workflowfm.simulator.metrics._
import java.util.UUID

object Task {
  sealed trait Priority extends Ordered[Priority] {
    def value:Int
    def compare(that:Priority) = this.value - that.value
  }
  case object Highest extends Priority { val value = 5 }
  case object High extends Priority { val value = 4 }
  case object Medium extends Priority { val value = 3 }
  case object Low extends Priority { val value = 2 }
  case object VeryLow extends Priority { val value = 1 }
}

class Task (
  val id: UUID,
  val name: String,
  val simulation: String,
  val actor: ActorRef,
  val created: Long,
  val resources: Seq[String],
  val duration: Long,
  val estimatedDuration: Long,
  val initialCost: Long,
  val interrupt: Int = Int.MaxValue,
  val priority: Task.Priority = Task.Medium
) extends Ordered[Task] {
  
  var cost:Long = initialCost
 
  def addCost(extra:Long) = cost += extra
  
  def nextPossibleStart(currentTime: Long, resourceMap: collection.Map[String,TaskResource]) = {
    (currentTime /: resources){ case (i,rN) => resourceMap.get(rN) match {
      case None => throw new RuntimeException(s"Resource $rN not found!")
      case Some(r) => Math.max(i,r.nextAvailableTimestamp(currentTime))
    }}
  }

  def taskResources(resourceMap: collection.Map[String,TaskResource]) = resources flatMap (resourceMap.get(_))

  
  def compare(that:Task) = {
    lazy val cPriority = that.priority.compare(this.priority)
    lazy val cResources = that.resources.size.compare(this.resources.size)
    lazy val cAge = this.created.compare(that.created)
    lazy val cDuration = that.estimatedDuration.compare(this.estimatedDuration)
    lazy val cInterrupt = this.interrupt.compare(that.interrupt)
    lazy val cID = this.id.compareTo(that.id)
    
    if (cPriority != 0) cPriority
    else if (cAge != 0) cAge
    else if (cResources != 0) cResources
    else if (cDuration != 0) cDuration
    else if (cInterrupt != 0) cInterrupt
    else cID
  }
  
  override def toString = {
    val res = resources.mkString(",")
    s"Task($name,$simulation,$created,[$res],$duration,$priority)"
  }
}

trait TaskGenerator {
  val createTime: Long
  def create(id: UUID, time: Long, actor: ActorRef, resources: String*): Task
}

object TaskGenerator {
  def apply (
    name: String,
    simulation: String,
    duration: ValueGenerator[Long],
    cost: ValueGenerator[Long],
    interrupt: Int = (-1),
    priority: Task.Priority = Task.Medium,
    createTime: Long = (-1)
  ) = SimpleTaskGenerator(name, simulation, duration, cost, interrupt, priority, createTime)
}

case class SimpleTaskGenerator (
  name: String,
  simulation: String,
  duration: ValueGenerator[Long],
  cost: ValueGenerator[Long],
  interrupt: Int = (-1),
  priority: Task.Priority = Task.Medium,
  override val createTime: Long = (-1)
) extends TaskGenerator {
  override def create(id: UUID, time: Long, actor: ActorRef, resources: String*) =
    new Task(id,name,simulation,actor,time,resources,duration.get,duration.estimate,cost.get,interrupt,priority)
  def withPriority(p:Task.Priority) = copy(priority = p)
  def withInterrupt(int:Int) = copy(interrupt = int)
  def withDuration(dur:ValueGenerator[Long]) = copy(duration = dur)
  def withName(n:String) = copy(name = n)
  def withSimulation(s:String) = copy(simulation=s)
  def withCreationTime(t:Long) = copy(createTime=t)
}
