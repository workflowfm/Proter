package com.workflowfm.proter

import java.util.UUID

case class Resource(name: String, capacity: Int, costPerTick: Double) {
  def start: ResourceState = ResourceState(this, Map())
}

case class ResourceState(resource: Resource, currentTasks: Map[UUID, (Long, TaskInstance)]) {
 // TODO add explicit remaining capacity to avoid too many calcs

  /**
    * True if the resource is idle, false otherwise.
    *
    * @return
    *   true if the resource is idle, false otherwise.
    */
  def hasCapacity: Boolean = currentTasks.size < resource.capacity

  def remainingCapacity: Int = 
    resource.capacity - currentTasks.map{
      case (_, (_, ti)) => ti.resourceQuantity(resource.name)
    }.sum

  /**
    * Attach a [[TaskInstance]] to this resource.
    *
    * If the resource is already attached to another [[TaskInstance]], the attached task is
    * returned. Otherwise, we return the updated [[ResourceState]].
    *
    * @param task
    *   The [[TaskInstance]] to attach.
    * @param currentTime
    *   The current (virtual) time.
    * @return
    *   Some [[TaskInstance]] that was already attached before or An updated [[ResourceState]] if
    *   the task was attached successfully.
    */
  def startTask( // TODO double check we are not running it twice
      task: TaskInstance,
      currentTime: Long
  ): Either[ResourceState.Full, ResourceState] = {
    if remainingCapacity < task.resourceQuantity(resource.name)
    then Left(ResourceState.Full(this))
    else Right(this.copy(currentTasks = currentTasks + (task.id -> (currentTime, task))))
  }

  def detach(taskID: UUID): ResourceState = 
    copy(currentTasks = currentTasks - taskID)

  def detach(taskIDs: Seq[UUID]): ResourceState = 
    copy(currentTasks = currentTasks -- taskIDs)

  def runningAnyOf(taskIDs: Seq[UUID]): Boolean = 
    taskIDs.exists(id => currentTasks.contains(id)) 

  /**
    * Estimates the earliest time the resource will become available.
    *
    * Lets the [[schedule.Scheduler Scheduler]] (via [[TaskInstance.nextPossibleStart]]) know an
    * '''estimate''' of when we expect to have this resource available again.
    *
    * This is based off of [[TaskInstance.estimatedDuration]] so may not be the accurate, but is
    * more realistic in terms of what we know at a specific given point in time.
    *
    * @param currentTime
    * @return
    *   the estimated earliest time the resource will become available
    */
  def nextAvailableTimestamp(currentTime: Long): Long = 
    if hasCapacity 
    then currentTime
    else currentTasks.values.map{ x => x._1 + x._2.estimatedDuration }.reduceLeft(_ min _) // TODO check this
                                                                                           // also shouldn't it be relevant to the desired capacity?
  def reduce(toReduce: Map[String, Int]): ResourceState = 
    copy(
      resource = resource.copy(
        capacity = resource.capacity - toReduce.get(resource.name).getOrElse(0)
      )
    )
}

object ResourceState {
  case class Full(state: ResourceState)
}

case class ResourceMap(resources: Map[String, ResourceState]) {

  /**
    * Add a new [[Resource]] to our map of available resources.
    *
    * @group resources
    * @param r
    *   The [[Resource]] to be added.
    */
  def addResource(r: Resource): ResourceMap = {
    if !resources.contains(r.name) then {
      copy(resources + (r.name -> r.start))
    } else this
  }

  /**
    * Add multiple [[Resource]]s in one go.
    *
    * @group resources
    * @param resources
    *   The sequence of [[Resource]]s to be added.
    */
  def addResources(resourcesToAdd: Seq[Resource]): ResourceMap =
    copy(resources ++ (resourcesToAdd.map { r => r.name -> r.start }))

  def finishTask(task: TaskInstance): ResourceMap = {
    copy(resources = resources.map { (n, r) => n -> r.detach(task.id) })
  }

  def startTask(task: TaskInstance, time: Long): Either[ResourceState.Full, ResourceMap] = {
    val update = for {
      (name, _) <- task.resources
      state <- resources.get(name)
    } yield (
      state.startTask(task, time).map { newState => name -> newState }
    )

    val folded =
      update.foldRight(Right(Nil): Either[ResourceState.Full, List[(String, ResourceState)]]) {
        (t, states) =>
          for {
            x <- t
            xs <- states
          } yield (x :: xs)
      }

    folded.map { stateUpdates => copy(resources = resources ++ stateUpdates) }
  }

  def stopTasks(ids: Seq[UUID]): (ResourceMap, Iterable[ResourceState]) = {
    val stopping = resources.filter { (_, r) => r.runningAnyOf(ids) }
    val result = copy(resources ++ stopping.map { (n, r) => n -> r.detach(ids) })
    (result, stopping.values)
  }

  def hasCapacity(r: String): Boolean = resources.get(r) match {
    case None => false
    case Some(resourceState) => resourceState.hasCapacity
  }

  def getAvailable(): ResourceMap = copy(resources = resources.filter(_._2.hasCapacity))

  // TODO improve to only go through selected resources
  def reduce(toReduce: Map[String, Int]): ResourceMap = copy(resources = resources.map((n, s) => n -> s.reduce(toReduce)))

  /**
    * The actual [[TaskResource]]s required. Retrieves the actual objects (instead of just their
    * names) from a map.
    *
    * @param resourceMap
    *   The map of available [[TaskResource]]s
    * @return
    *   The [[TaskResource]]s required for this task.
    */
  def get(task: TaskInstance): Seq[ResourceState] =
    task.resources.flatMap((n, _) => resources.get(n)).toSeq

  def canHandle(task: TaskInstance): Boolean = 
    task.resources.forall { (r, q) =>
      resources.get(r).map(_.remainingCapacity >= q).getOrElse(false)
    }

  def remainingCapacityOf(name: String): Int = resources.get(name).map(_.remainingCapacity).getOrElse(0)

  def capacityOf(name: String): Int = resources.get(name).map(_.resource.capacity).getOrElse(0)
}

object ResourceMap {

  def apply(resources: Seq[Resource]): ResourceMap = ResourceMap(
    Map() ++ resources.map { r => r.name -> r.start }
  )
}
