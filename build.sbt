name := "PlayConcurrentStream"

version := "1.0"

lazy val `playconcurrentstream` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  cache,
  specs2 % Test,
  "com.h2database" % "h2" % "1.4.187",
  "com.typesafe.play" %% "play-slick" % "1.0.0-RC3",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.0.0-RC3"
)

scalacOptions += "-feature"

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )
