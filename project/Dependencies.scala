package com.workflowfm.proter

import sbt._

object Dependencies {

  val scalaVer = "3.1.0"

  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.10" % "test"

  val cats = "org.typelevel" %% "cats-effect" % "3.3.7"
  val catsTest = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test

  val fs2 = "co.fs2" %% "fs2-core" % "3.2.5"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.6.18"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.6.18" % Test

  val apache = "org.apache.commons" % "commons-lang3" % "3.3.2"

  val sortImports = "com.nequissimus" %% "sort-imports" % "0.6.1"

  val common: Seq[ModuleID] = Seq(
    apache,
    cats,
    fs2,
  )

  val testAll: Seq[ModuleID] = Seq(
    scalaTest,
    catsTest,
  )

  val akka: Seq[ModuleID] = Seq (
    akkaActor,
    akkaTestkit,
  )
}


