organization := "org.rebeam"
name := "boxes-http4s"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blazeserver" % "0.8.4",
  "org.http4s" %% "http4s-dsl"         % "0.8.4",
  "org.http4s" %% "http4s-argonaut"    % "0.8.4"
)
