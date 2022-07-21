package com.workflowfm.proter
package metrics

import com.workflowfm.proter.*

/**
  * Metrics for at [[TaskResource]].
  *
  * @param name
  *   the unique name of the [[TaskResource]]
  * @param busyTime
  *   the total amount of virtual time that the [[TaskResource]] has been busy, i.e. attached to a
  *   [[TaskInstance]]
  * @param idleTime
  *   the total amount of virtual time that the [[TaskResource]] has been idle, i.e. not attached to
  *   any [[TaskInstance]]
  * @param tasks
  *   the number of different [[TaskInstance]]s that have been attached to this [[TaskResource]]
  * @param cost
  *   the total cost associated with this [[TaskResource]]
  */
final case class ResourceMetrics(
    name: String,
    costPerTick: Double,
    idleUpdate: Long,
    busyTime: Long,
    idleTime: Long,
    tasks: Int,
    cost: Double
) {

  def start(t: Long): ResourceMetrics = copy(idleUpdate = t)

  /** Adds some idle time to the total. */
  def idle(t: Long): ResourceMetrics =
    if idleUpdate < t then copy(idleTime = idleTime + t - idleUpdate, idleUpdate = t) else this

  /** Updates the metrics given a new [[TaskInstance]] has been attached to the [[TaskResource]]. */
  def task(t: Long): ResourceMetrics = copy(
    tasks = tasks + 1,
    idleUpdate = t
  )

  /** Updates the metrics given a new [[TaskInstance]] has been attached to the [[TaskResource]]. */
  def endTask(t: Long, start: Long, tcost: Double): ResourceMetrics = copy(
    cost = cost + tcost,
    busyTime = busyTime + t - start,
    idleUpdate = t
  )
}

object ResourceMetrics {

  /** Initialize metrics given the name of a [[TaskResource]]. */
  def apply(t: Long, r: Resource): ResourceMetrics =
    ResourceMetrics(r.name, r.costPerTick, t, 0L, 0L, 0, 0)
}
