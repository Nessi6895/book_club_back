enablePlugins(DockerPlugin)

val scala3Version = "3.3.1"
val zioVersion = "2.0.18"
val zioHttpVersion = "3.0.0-RC3"
val ydbVersion = "2.1.9"
val circeVersion = "0.14.6"

lazy val root = project
  .in(file("."))
  .settings(
    name := "book_club_back",
    organization := "bmo6895",
    version := "0.0.1",
    scalaVersion := scala3Version,
    Compile / run / mainClass := Some("com.nessi.book_club.Main"),
    assembly / mainClass := Some("com.nessi.book_club.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) =>
        (xs map { _.toLowerCase }) match {
          case (x :: Nil)
              if Seq("manifest.mf", "index.list", "dependencies") contains x =>
            MergeStrategy.discard
          case ps @ (x :: xs)
              if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") || ps.last
                .endsWith(".rsa") =>
            MergeStrategy.discard
          case _ => MergeStrategy.first
        }
      case pathList if pathList.endsWith(".class") => MergeStrategy.first
      case pathList if pathList.endsWith(".conf")  => MergeStrategy.concat
      case s => (assembly / assemblyMergeStrategy).value(s)
    },
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "tech.ydb" % "ydb-sdk-core" % ydbVersion,
      "tech.ydb" % "ydb-sdk-table" % ydbVersion,
      "tech.ydb.auth" % "yc-auth-provider" % "2.1.1",
      "tech.ydb.auth" % "yc-auth-provider-shaded" % "2.1.1",
      "dev.zio" %% "izumi-reflect" % "2.3.8",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.4",
      "com.github.pureconfig" % "pureconfig-generic_2.13" % "0.17.4" exclude ("com.github.pureconfig", "pureconfig-core_2.13")
    )
  )

docker / dockerfile := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"
  val certFilePath = sys.env.get("CERT_FILE").get
  val destCertPath = ".cert/cert_file.sa"

  new Dockerfile {
    from("openjdk:21-jdk-slim")
    add(artifact, artifactTargetPath)
    add(file(certFilePath), destCertPath)
    customInstruction("EXPOSE", "8080/tcp")
    arg("CONN_STRING")
    arg("SERT_FILE")
    env(
      "CONN_STRING" -> sys.env.get("CONN_STRING").get,
      "CERT_FILE" -> destCertPath
    )
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

docker / buildOptions := BuildOptions(
  cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always,
  platforms = List("linux/amd64")
)

docker / imageNames := Seq(
  // Sets the latest tag
  ImageName(s"${organization.value}/${name.value}:latest"),

  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some(organization.value),
    repository = name.value,
    tag = Some("v" + version.value)
  )
)
