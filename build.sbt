organization := "org.rebeam"
name := "boxes-http4s"
version := "0.1-SNAPSHOT"
scalaVersion := "2.11.6"

resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % "0.9.0-SNAPSHOT",
  "org.http4s" %% "http4s-dsl"         % "0.9.0-SNAPSHOT",
  "org.http4s" %% "http4s-argonaut"    % "0.9.0-SNAPSHOT",
  "org.http4s" %% "http4s-websocket"    % "0.1.1",
  "org.rebeam" %% "boxes-core" % "0.1-SNAPSHOT"
)

fork in run := true
