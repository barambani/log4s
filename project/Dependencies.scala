import sbt._
import Keys._

object Dependencies {
  final val slf4jVersion      = "1.7.25"
  final val logbackVersion    = "1.2.3"
  final val scalacheckVersion = "1.14.0"

  val scalatestVersion = Def.map(scalaBinaryVersion) {
    case "2.13.0-M3" => "3.0.5-M1"
    case "2.13.0-M5" => "3.0.6-SNAP6"
    case other       => "3.0.5"
  }

  val slf4j     = "org.slf4j"      % "slf4j-api"       % slf4jVersion
  val logback   = "ch.qos.logback" % "logback-classic" % logbackVersion

  val reflect = Def.map(scalaVersion)("org.scala-lang" % "scala-reflect" % _)
}
