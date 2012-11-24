import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.2.0")
@classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.4/ant-1.8.4.jar")
class SBuild(implicit project: Project) {

  val version = "0.2.0-SNAPSHOT"
  val jar = "target/de.tototec.cmdoption-" + version + ".jar"

  SchemeHandler("mvn", new MvnSchemeHandler())

  val testCp = "mvn:org.testng:testng:6.4"

  ExportDependencies("eclipse.classpath", testCp)

  val javaFilesWithMessages = (Path("src/main/java/de/tototec/cmdoption").listFiles ++
    Path("src/main/java/de/tototec/cmdoption/handler").listFiles).
    filter(_.getName.endsWith(".java"))

  val poFiles = Path("src/main/po").listFiles.filter(f => f.getName.endsWith(".po"))

  Target("phony:all") dependsOn jar

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile") exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/main/java"), Path("target"), ctx) {
      AntMkdir(dir = Path("target/classes"))
      AntJavac(source = "1.5", target = "1.5", encoding = "UTF-8",
        debug = true, fork = true, includeAntRuntime = false,
        srcDir = AntPath("src/main/java"),
        destDir = Path("target/classes")
      )
    }
  }

  val msgCatalog = Path("target/po/messages.pot")

  Target(msgCatalog) dependsOn javaFilesWithMessages.map { TargetRef(_) }.toSeq exec { ctx: TargetContext =>
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
        "--output", ctx.targetFile.get.getName) ++ ctx.fileDependencies.map(file => srcDirUri.relativize(file.toURI).getPath)
    )
  }

  val propFileTargets = poFiles.map { poFile =>
    val propFile = Path("target/classes/de/tototec/cmdoption", "Message_" + """\.po$""".r.replaceFirstIn(poFile.getName, ".properties"))
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
    AntJar(baseDir = Path("target/classes"), destFile = Path(jar))
  }

  propFileTargets.foreach { t => jarTarget dependsOn t }

  Target("phony:installToMvn") dependsOn jar exec {
    AntExec(
      executable = "mvn", 
      args = Array("install:install-file", "-DgroupId=de.tototec", "-DartifactId=de.tototec.cmdoption", "-Dversion=" + version, "-Dfile=" + jar, "-DgeneratePom=true", "-Dpackaging=jar"))
  } help "Install jar into Maven repository."

}
