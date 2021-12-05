ThisBuild / scalaVersion := "3.1.0"

lazy val core = project.in(file("."))
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.15.0-M1",
  "org.typelevel" %% "jawn-parser" % "1.3.0"
)
