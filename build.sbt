import com.workflowfm.proter.Dependencies

enablePlugins(HugoPlugin)
enablePlugins(SiteScaladocPlugin)
enablePlugins(GhpagesPlugin)


inThisBuild(List(
  organization := "com.workflowfm",
  organizationName := "WorkflowFM",
  organizationHomepage := Some(url("http://www.workflowfm.com/")),
  homepage := Some(url("http://www.workflowfm.com/proter/")),
  description := "A discrete event simulator for asynchronous prioritized processes",
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),

  developers := List(
    Developer(
      id = "PetrosPapapa",
      name = "Petros Papapanagiotou",
      email = "petros@workflowfm.com",
      url = url("https://homepages.inf.ed.ac.uk/ppapapan/")
    )
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/workflowfm/Proter"),
      "scm:git:git@github.com:workflowfm/Proter.git"
    )
  ),
  dynverSonatypeSnapshots := true,

  scalafixDependencies += Dependencies.sortImports,
  git.remoteRepo := scmInfo.value.get.connection.replace("scm:git:", "")
))

// Publish to Sonatype / Maven Central

ThisBuild / publishTo := sonatypePublishToBundle.value
pomIncludeRepository := { _ => false }
publishMavenStyle := true
sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"


// Website generation with sbt-site

Hugo / sourceDirectory := file("docs")
Hugo / baseURL := uri("http://docs.workflowfm.com/proter")
//baseURL in Hugo := uri("./")
Hugo / includeFilter := ("*")

ghpagesNoJekyll := true
previewFixedPort := Some(9999)

//ThisBuild / scalafixScalaBinaryVersion := "3.1"

lazy val commonSettings = Seq(
  scalaVersion := Dependencies.scalaVer,

  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,

 // scalacOptions += "-Wunused:imports", // required by `RemoveUnused` rule
 // scalacOptions += "-deprecation",
 // scalacOptions += "-feature",

  scalacOptions ++= {
    Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-deprecation",
      //"-language:implicitConversions",
      // "-Xfatal-warnings",
      "-unchecked",
//      "-source:3.0-migration",
//      "-rewrite",
//      "-new-syntax"
//      "-source:future",
      //"-Xmax-inlines:40" // to allow `deriveEncoder[Event]`
      )
  },

  autoAPIMappings := true,
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-diagrams", "-diagrams-debug"),

)



lazy val aggregatedProjects: Seq[ProjectReference] = List[ProjectReference](
  proter,
)

def proterModule(name: String): Project = 
  Project(id = name, base = file(name))
    .settings(commonSettings)
    .settings(libraryDependencies ++= Dependencies.common)
    .settings(libraryDependencies ++= Dependencies.testAll)
    .dependsOn(proter % "compile->compile;test->test")

import com.fgrutsch.sbt.swaggerui.SwaggerUiConfig

lazy val root = Project(id = "proter-root", base = file("."))
  .enablePlugins(ScalaUnidocPlugin, SwaggerUiPlugin)
  .settings(commonSettings)
  .settings( 
    publishArtifact := false,
    ScalaUnidoc / siteSubdirName := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    swaggerUiConfig := swaggerUiConfig.value.addUrls(
      SwaggerUiConfig.Url.File(
        "Proter Server",
        proterServer.base / "docs" / "proter-server-api.yaml"
      ),
    ),
    swaggerUiVersion := "4.13.0",
    makeSite / siteSubdirName := "server-api",
    addMappingsToSiteDir(swaggerUiGenerate / mappings, makeSite / siteSubdirName),
  )
  .aggregate(aggregatedProjects: _*)


lazy val proter = Project(id = "proter", base = file("proter"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Dependencies.common,
    libraryDependencies ++= Dependencies.testAll,
  )

lazy val proterServer = proterModule("proter-server")
  .settings(
    libraryDependencies ++= Dependencies.server,
    libraryDependencies ++= Dependencies.testServer,
    assembly / mainClass := Some("com.workflowfm.proter.server.Main"),
    assembly / assemblyJarName := s"${name.value}_${version.value}.jar",
  )


