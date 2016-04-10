organization := "org.rebeam"
name := "boxes-http4s"
version := "0.1-SNAPSHOT"
scalaVersion := "2.11.7"
resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"

lazy val http4sVersion = "0.12.4"
lazy val argonautVersion = "6.1"

libraryDependencies ++= Seq(
  "org.http4s"  %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"  %% "http4s-dsl"           % http4sVersion,
  "org.http4s"  %% "http4s-argonaut"      % http4sVersion,

  "org.rebeam"  %% "boxes-core"           % "0.1-SNAPSHOT",
  
  "io.argonaut" %% "argonaut"             % argonautVersion,
  
  "org.log4s"   %% "log4s"                % "1.2.1",
  
  "org.slf4j"   % "slf4j-simple"          % "1.7.21"
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint"
)

fork in run := true

//SLF4J simple logger, y u log to System.err by default, even for info?
javaOptions := Seq("-Dorg.slf4j.simpleLogger.logFile=System.out")

