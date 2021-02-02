package com.workflowfm.proter.controller

import scala.math.log

trait ArrivalRate {
    def next(time: Double): Double

}

case class ConstantRate(period: Double) extends ArrivalRate {
    override def next(time: Double): Double = time + period
}

case class NegativeExponentialRate(lambda: Double) extends ArrivalRate {
    override def next(time: Double): Double =  {
        val rand = new util.Random().nextDouble()
        /**
          * Density function:
          *  fx(t) = le^(-lt) , where l is lambda, t is time
          *  
          * Cumulative distribution:
          * Fx(t) = 1-e(-lt)
          * 
          * given a random number r, let
          * r = Fx(t)
          * => t = -ln(1-r)/l
          * => t = -ln(r)/l , since r is random between 0 and 1 we can use r and 1-r interchangably.
          */
        return time-(log(rand)/lambda)
    }
}