ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "com.imperx"
ThisBuild / publishMavenStyle := true

lazy val commonTestDeps = Seq(
  "org.scalameta" %% "munit" % "1.0.2" % Test
)

lazy val root = (project in file("."))
  .aggregate(imperxCore, imperxNative, imperxTests)
  .settings(
    name := "imperx-camera",
    publish / skip := true
  )

lazy val imperxCore = (project in file("imperx-core"))
  .settings(
    name := "imperx-camera-core",
    libraryDependencies ++= commonTestDeps ++ Seq(
      "com.github.jnr" % "jnr-ffi" % "2.2.17"
    )
  )

lazy val imperxNative = (project in file("imperx-native"))
  .settings(
    name := "imperx-native",
    publish / skip := true,
    libraryDependencies ++= commonTestDeps
  )
  .dependsOn(imperxCore)

lazy val imperxTests = (project in file("imperx-tests"))
  .settings(
    name := "imperx-tests",
    publish / skip := true,
    libraryDependencies ++= commonTestDeps ++ Seq(
      "gov.nasa.gsfc.heasarc" % "nom-tam-fits" % "1.21.0"
    ),
    Test / fork := true
  )
  .dependsOn(imperxCore, imperxNative)
