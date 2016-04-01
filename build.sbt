organization := "org.rebeam"
name := "boxes-http4s"
version := "0.1-SNAPSHOT"
scalaVersion := "2.11.7"
resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"

lazy val http4sVersion = "0.12.4"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl"         % http4sVersion,
  "org.http4s" %% "http4s-argonaut"    % http4sVersion,
  "org.rebeam" %% "boxes-core" % "0.1-SNAPSHOT"
)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

fork in run := true
