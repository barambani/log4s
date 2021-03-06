import Dependencies._
import ReleaseTransformations._

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

/* TODO: Attempts to do this with the existing `TaskKey` have failed, but that
 * would be better than doing it using the string. This approach also won't
 * work if you were to dynamically modify the cross-build settings. The key is
 * autoimported and named `mimaReportBinaryIssues`. */
lazy val binaryCompatStep = releaseStepCommandAndRemaining("+mimaReportBinaryIssues")
/* This is the standard release process plus a binary compat check after tests */
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  binaryCompatStep,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
val prevVersions = settingKey[Set[String]]("The previous versions for the current project")
val prevArtifacts = Def.derive {
  mimaPreviousArtifacts := {
    /* TODO: I imagine there's a better way to do this */
    val artName = {
      val suffix = if (isScalaJSProject.value) "_sjs0.6" else ""
      artifact.value.name + suffix
    }
    prevVersions.value.map(organization.value %% artName % _)
  }
}

def jsOpts = new Def.SettingList(Seq(
  scalacOptions += "-P:scalajs:sjsDefinedByDefault",
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
))

lazy val root: Project = (project in file ("."))
  .enablePlugins(BasicSettings)
  .settings(Publish.settings: _*)
  .settings(Release.settings: _*)
  .aggregate(coreJVM, coreJS, testingJVM, testingJS)
  .settings (
    name := "Log4s Root",

    publish := {},
    publishLocal := {},
    publishArtifact := false,

    exportJars := false,

    skip in Compile := true,
    skip in Test := true
  )

lazy val jsPrevVersions = Set.empty[String]

lazy val core = (crossProject(JSPlatform, JVMPlatform) in file ("core"))
  .enablePlugins(BasicSettings, SiteSettingsPlugin)
  .dependsOn(testing % "test")
  .settings(Publish.settings: _*)
  .settings(Release.settings: _*)
  .settings(
    name := "Log4s",
    prevArtifacts,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import ProblemFilters.exclude
      /* import com.typesafe.tools.mima.core.ProblemFilters._ */
      /* These macros are not part of the runtime and are not a binary compatibility concern */
      Seq(
        exclude[IncompatibleResultTypeProblem]("org.log4s.LoggerMacros.*"),
        exclude[IncompatibleMethTypeProblem]("org.log4s.LoggerMacros.getLoggerImpl"),
      )
    },

    libraryDependencies ++= Seq (
      slf4j,
      logback          %   "test",
      "org.scalacheck" %%% "scalacheck" % scalacheckVersion % "test",
      "org.scalatest"  %%% "scalatest"  % scalatestVersion.value % "test",
      reflect.value    %   "provided"
    ),

    unmanagedSourceDirectories in Compile ++= {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" =>
          Seq.empty
        case _ =>
          Seq(baseDirectory.value / ".." / "shared" / "src" / "main" / "scala-2.11")
      }
    },

    unmanagedSourceDirectories in Compile ++= {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" | "2.12" =>
          Seq(baseDirectory.value / ".." / "shared" / "src" / "main" / "scala-oldcoll")
        case _ =>
          Seq(baseDirectory.value / ".." / "shared" / "src" / "main" / "scala-newcoll")
      }
    }

  )
  .jvmSettings(
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided",
    prevVersions := {
      /* I'm using the first & last version of each minor release rather than
       * including every single patch-level update. */
      def `2.11Versions` = Set("1.0.3", "1.0.5", "1.1.0", "1.1.5", "1.2.0", "1.2.1", "1.3.0")
      def `2.12Versions` = Set("1.3.3", "1.3.6", "1.4.0", "1.5.0", "1.6.0", "1.6.1")
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" => `2.11Versions` ++ `2.12Versions`
        case "2.12"          => `2.12Versions`
        case "2.13.0-M2"     => Set("1.5.0")
        case other           =>
          sLog.value.info(s"No known MIMA artifacts for: $other")
          Set.empty
      }
    }
  )
  .jsSettings(jsOpts)
  .jsSettings(
    prevVersions := jsPrevVersions
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val testing = (crossProject(JSPlatform, JVMPlatform) in file ("testing"))
  .enablePlugins(BasicSettings, SiteSettingsPlugin)
  .settings(Publish.settings: _*)
  .settings(Release.settings: _*)
  .settings(
    name := "Log4s Testing",
    description := "Utilities to help with build-time unit tests for logging",
    prevArtifacts,
    libraryDependencies ++= Seq (
      slf4j,
      logback
    )
  )
  .jvmSettings(
    prevVersions := {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" | "2.12" | "2.13.0-M2" =>
          Set("1.5.0", "1.6.0", "1.6.1")
        case "2.13.0-M3" =>
          Set("1.6.0", "1.6.1")
        case other =>
          Set.empty
      }
    }
  )
  .jsSettings(jsOpts)
  .jsSettings(
    prevVersions := jsPrevVersions
  )

lazy val testingJS = testing.js
lazy val testingJVM = testing.jvm
