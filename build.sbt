organization := "com.inbaytech"

name := "akka-pusher"

scalaVersion := "2.12.8"
crossScalaVersions := Seq("2.12.8", "2.13.0")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

val akkaV      = "2.5.23"
val akkaHttpV  = "10.1.8"
val sprayJsonV = "1.3.4"
val specs2V    = "4.3.6"

val developmentDependencies = Seq(
  "com.typesafe.akka"       %%  "akka-actor"                        % akkaV,
  "com.typesafe.akka"       %%  "akka-stream"                       % akkaV,
  "com.typesafe.akka"       %%  "akka-http"                         % akkaHttpV,
  "io.spray"                %% "spray-json"                         % sprayJsonV,
  "com.github.nscala-time"  %%  "nscala-time"                       % "2.20.0",
  "org.slf4j"               %   "slf4j-api"                         % "1.7.19",
  "com.iheart"              %%  "ficus"                             % "1.4.4"
)
val testDependencies = Seq(
  "com.typesafe.akka"   %%  "akka-testkit"  % akkaV % "test",
  "org.specs2"          %%  "specs2-core"   % specs2V % "test",
  "org.specs2"          %%  "specs2-matcher" % specs2V % "test",
  "org.specs2"          %%  "specs2-matcher-extra" % specs2V % "test",
  "org.specs2"          %%  "specs2-mock"   % specs2V % "test"
)
libraryDependencies ++= developmentDependencies ++ testDependencies

fork in Test := true
parallelExecution in Test := true
javaOptions in Test ++= Seq(
  s"-Djava.util.Arrays.useLegacyMergeSort=true"
)

publishArtifact in Test := false
publishMavenStyle := true
pomIncludeRepository := { _ => false }
pomExtra := (
  <url>http://github.com/dtaniwaki/akka-pusher</url>
    <licenses>
      <license>
        <name>MIT</name>
        <url>http://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/dtaniwaki/akka-pusher.git</connection>
      <developerConnection>scm:git:git@github.com:dtaniwaki/akka-pusher.git</developerConnection>
      <url>github.com/dtaniwaki/akka-pusher</url>
    </scm>
    <developers>
      <developer>
        <id>dtaniwaki</id>
        <name>Daisuke Taniwaki</name>
        <url>https://github.com/dtaniwaki</url>
      </developer>
    </developers>
  )

val props = sys.props
val user = props.get("user.name").getOrElse("USER")
val nexus = props.get("sbt.publish.host").orElse(sys.env.get("SBT_PUBLISH_HOST"))
val repository = props.get("sbt.publish.repo").orElse(sys.env.get("SBT_PUBLISH_REPO"))
val myM2Resolver = Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))

def publishTarget: Option[Resolver] = nexus match {
  case Some(host) => repository map { repo =>
    s"$repo" at s"http://$host/content/repositories/$repo"
  }
  case None => Some(myM2Resolver)
}

def ivyCredentials(resolver: Resolver): Seq[Credentials] = resolver match {
  case MavenRepository(n, r) =>
    Seq[Credentials](Credentials(Path.userHome / ".ivy2" / "credentials" / uri(r).getHost))
  case _ => Seq[Credentials]()
}

publishTo := publishTarget
credentials ++= publishTo.value.fold {
  Seq[Credentials]()
} { repository =>
  ivyCredentials(repository)
}
