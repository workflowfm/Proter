ThisBuild / version          := "0.3-alpha-SNAPSHOT"
ThisBuild / organization     := "com.workflowfm"
ThisBuild / scalaVersion     := "2.12.6"

ThisBuild / autoAPIMappings  := true
// Fixes some sbt import problems.
// https://github.com/sbt/sbt-native-packager/issues/1063
resolvers += Resolver.sbtPluginRepo("releases")

lazy val root = (project in file("."))
  .settings(
    name := "wfm-simulator",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.0-SNAP10" % Test,
    libraryDependencies +=  "org.scalacheck" %% "scalacheck" % "1.14.0" % Test,

    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.1",
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.6.1" % "test",

    libraryDependencies += "com.typesafe.akka" %% "akka-stream"       % "2.6.1",

    libraryDependencies += "uk.ac.ed.inf" %% "subakka" % "0.1-SNAPSHOT",
    libraryDependencies += "uk.ac.ed.inf" %% "subakka" % "0.1-SNAPSHOT" % Test  classifier "tests",

    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2",

    libraryDependencies += "junit" % "junit" % "4.8.2"
)
