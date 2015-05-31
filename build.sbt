lazy val `playconcurrentuploader` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  cache,
  specs2 % Test,
  "com.h2database" % "h2" % "1.4.187",
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % Test,
  "com.typesafe.play" %% "play-slick" % "1.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.0.0"
)

name := "PlayConcurrentStream"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

scalacOptions += "-feature"

scalaVersion := "2.11.6"

unmanagedResourceDirectories in Test <+= baseDirectory ( _ /"target/web/public/test" )

version := "1.0"
