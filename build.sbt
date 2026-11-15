ThisBuild / organization := "com.jalalix.scala"
ThisBuild / version := "0.2.0"
ThisBuild / scalaVersion := "2.13.15"

ThisBuild / organizationName := "JalaliX"
ThisBuild / organizationHomepage := Some(url("https://jalalix.com"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/jalalix/scala"),
    "scm:git:git://github.com/jalalix/scala.git",
    "scm:git:ssh://git@github.com/jalalix/scala.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "maklavani",
    name = "Hossein Maklavani",
    email = "hos.mohamadi@gmail.com",
    url = url("https://github.com/maklavani")
  )
)

ThisBuild / description := "Jalali (Persian / Shamsi) calendar for Scala. Sponsor: https://opencollective.com/jalalix"
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("https://jalalix.com"))
ThisBuild / pomExtra :=
  <properties>
    <opencollective>https://opencollective.com/jalalix</opencollective>
  </properties>

ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true

ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

// Prefer gpg CLI so signatures include issuer fingerprint (required by Central).
ThisBuild / useGpg := true

lazy val root = (project in file("."))
  .settings(
    name := "jalalix",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "com.lihaoyi" %% "ujson" % "4.0.2" % Test
    )
  )
