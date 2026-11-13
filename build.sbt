ThisBuild / scalaVersion := "2.13.17"
ThisBuild / version := "1.0.0"

name := "student-records-mongodb"

libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "5.5.1"

scalacOptions ++= Seq("-release:11", "-deprecation", "-feature", "-unchecked")

Compile / mainClass := Some("StudentRecords")
fork := true
connectInput := true

assembly / assemblyJarName := "student-records.jar"
assembly / assemblyMergeStrategy := {
  case path if path.endsWith("module-info.class") => MergeStrategy.discard
  case path if path.startsWith("META-INF/native-image/") => MergeStrategy.discard
  case path => (assembly / assemblyMergeStrategy).value(path)
}
