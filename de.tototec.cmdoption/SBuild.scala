import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.4.0")
@include("../CmdOption.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val version = CmdOption.version

  val jar = s"target/de.tototec.cmdoption-$version.jar"
  val sourcesJar = jar.substring(0, jar.length - 4) + "-sources.jar"
  val javadocJar = jar.substring(0, jar.length - 4) + "-javadoc.jar"

  val testCp =
    "mvn:org.testng:testng:6.4" ~
    "mvn:com.beust:jcommander:1.30" ~ // transitive required by testng
    "mvn:org.scalatest:scalatest_2.10:1.9.1" ~
    "mvn:org.scala-lang:scala-library:2.10.1" ~
    "mvn:org.scala-lang:scala-actors:2.10.1"

  ExportDependencies("eclipse.classpath", testCp)

  val poFiles = Path("src/main/po").listFiles.filter(f => f.getName.endsWith(".po"))

  Target("phony:all") dependsOn jar ~  sourcesJar ~ "test"

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile").cacheable dependsOn "scan:src/main/java" exec {
    addons.java.Javac(
      source = "1.5", target = "1.5", encoding = "UTF-8", debugInfo = "all",
      destDir = Path("target/classes"),
      sources = "scan:src/main/java".files
    )
  }

  Target("phony:compileTest").cacheable dependsOn testCp ~ jar ~ "scan:src/test/java" exec {
    addons.java.Javac(
      source = "1.5", target = "1.5", encoding = "UTF-8", debugInfo = "all",
      destDir = Path("target/test-classes"),
      sources = "scan:src/test/java".files,
      classpath = testCp.files ++ jar.files
    )
  }

  Target("phony:test") dependsOn "compileTest" ~ testCp ~ jar exec {
    AntJava(
      failOnError = true, dir = Path("target"), fork = true,
      classpath = AntPath(locations = testCp.files ++ jar.files ++ Seq(Path("target/test-classes"), Path("src/test/resources"))),
      className = "org.scalatest.tools.Runner",
      arguments = Seq("-oF", "-b", Path("src/test/resources/TestNGSuite.xml").getPath)
    )
  }

  val msgCatalog = Path("target/po/messages.pot")

  Target(msgCatalog) dependsOn "scan:src/main/java" exec { ctx: TargetContext =>
    AntMkdir(dir = ctx.targetFile.get.getParentFile)

    import java.io.File
    val srcDirUri = Path("src/main/java").toURI

    AntExec(
      failOnError = true,
      executable = "xgettext",
      args = Array[String](
        "-ktr", "-kmarktr",
        "--directory", new File(srcDirUri).getPath,
        "--output-dir", ctx.targetFile.get.getParent,
        "--output", ctx.targetFile.get.getName) ++ "scan:src/main/java".files.map(file => srcDirUri.relativize(file.toURI).getPath)
    )
  }

  val propFileTargets = poFiles.map { poFile =>
    val propFile = Path("target/classes/de/tototec/cmdoption", "Messages_" + """\.po$""".r.replaceFirstIn(poFile.getName, ".properties"))
    Target(propFile) dependsOn (msgCatalog ~ poFile) exec {
      AntMkdir(dir = propFile.getParentFile)
      AntExec(
        failOnError = true,
        executable = "msgmerge",
        args = Array("--output-file", propFile.getPath, "--properties-output", poFile.getPath, msgCatalog.getPath)
      )
    }
  }

  Target("phony:msgmerge") dependsOn msgCatalog exec {
    poFiles.foreach { poFile =>
      AntExec(failOnError = true, executable = "msgmerge",
        args = Array("--update", poFile.getPath, msgCatalog.getPath))
    }
  } help "Updates translation files (.po) with newest messages."

  val jarTarget = Target(jar) dependsOn ("compile") exec {
    AntJar(baseDir = Path("target/classes"), destFile = Path(jar), fileSet = AntFileSet(file = Path("LICENSE.txt")))
  }

  propFileTargets.foreach { t => jarTarget dependsOn t }

  Target("phony:installToMvn") dependsOn jar exec {
    AntExec(
      executable = "mvn",
      args = Array("install:install-file", "-DgroupId=de.tototec", "-DartifactId=de.tototec.cmdoption", "-Dversion=" + version, "-Dfile=" + jar, "-DgeneratePom=true", "-Dpackaging=jar"))
  } help "Install jar into Maven repository."

  Target(sourcesJar) exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/main/"), Path("target"), ctx) {
      AntJar(destFile = ctx.targetFile.get, fileSets = Seq(
        AntFileSet(dir = Path("src/main/java")),
        AntFileSet(dir = Path("src/main/po")),
        AntFileSet(file = Path("LICENSE.txt"))
      ))
    }
  }

  Target(javadocJar) exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/main/java"), Path("target"), ctx) {

      val docDir = Path("target/javadoc")
      AntMkdir(dir = docDir)

      new org.apache.tools.ant.taskdefs.Javadoc() {
        setProject(AntProject())
        setSourcepath(AntPath(location = Path("src/main/java")))
        setDestdir(docDir)
      }.execute

      AntJar(destFile = ctx.targetFile.get, baseDir = docDir)
    }
  }

}
