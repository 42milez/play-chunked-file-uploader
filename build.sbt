name := "PlayConcurrentStream"

version := "1.0"

lazy val `playconcurrentstream` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq( jdbc , anorm , cache , ws )

scalacOptions += "-feature"

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )
