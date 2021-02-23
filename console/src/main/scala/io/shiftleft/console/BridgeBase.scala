package io.shiftleft.console

import ammonite.ops.pwd
import ammonite.ops.Path
import ammonite.util.{Colors, Res}
import better.files._
import io.shiftleft.console.embammonite.EmbeddedAmmonite
import io.shiftleft.console.cpgqlserver.CPGQLServer

import java.io.{FileOutputStream, PrintStream}

case class Config(
    scriptFile: Option[Path] = None,
    command: Option[String] = None,
    params: Map[String, String] = Map.empty,
    additionalImports: List[Path] = Nil,
    addPlugin: Option[String] = None,
    rmPlugin: Option[String] = None,
    pluginToRun: Option[String] = None,
    listPlugins: Boolean = false,
    src: Option[String] = None,
    language: Option[String] = None,
    overwrite: Boolean = false,
    store: Boolean = false,
    server: Boolean = false,
    serverHost: String = "localhost",
    serverPort: Int = 8080,
    serverAuthUsername: String = "",
    serverAuthPassword: String = "",
    nocolors: Boolean = false
)

/**
  * Base class for Ammonite Bridge. Nothing to see here, move along.
  * */
trait BridgeBase {

  protected def parseConfig(args: Array[String]): Config = {
    implicit def pathRead: scopt.Read[Path] =
      scopt.Read.stringRead
        .map(Path(_, pwd)) //support both relative and absolute paths

    val parser = new scopt.OptionParser[Config]("(joern|ocular)") {
      override def errorOnUnknownArgument = false

      note("Script execution")

      opt[Path]("script")
        .action((x, c) => c.copy(scriptFile = Some(x)))
        .text("path to script file: will execute and exit")

      opt[Map[String, String]]('p', "params")
        .valueName("k1=v1,k2=v2")
        .action((x, c) => c.copy(params = x))
        .text("key values for script")

      opt[Seq[Path]]("import")
        .valueName("script1.sc,script2.sc,...")
        .action((x, c) => c.copy(additionalImports = x.toList))
        .text("import additional additional script(s): will execute and keep console open")

      opt[String]("command")
        .action((x, c) => c.copy(command = Some(x)))
        .text("select one of multiple @main methods")

      note("Plugin Management")

      opt[String]("add-plugin")
        .action((x, c) => c.copy(addPlugin = Some(x)))
        .text("Plugin zip file to add to the installation")

      opt[String]("remove-plugin")
        .action((x, c) => c.copy(rmPlugin = Some(x)))
        .text("Name of plugin to remove from the installation")

      opt[Unit]("plugins")
        .action((_, c) => c.copy(listPlugins = true))
        .text("List available plugins and layer creators")

      opt[String]("run")
        .action((x, c) => c.copy(pluginToRun = Some(x)))
        .text("Run layer creator. Get a list via --plugins")

      opt[String]("src")
        .action((x, c) => c.copy(src = Some(x)))
        .text("Source code directory to run layer creator on")

      opt[String]("language")
        .action((x, c) => c.copy(language = Some(x)))
        .text("Language to use in CPG creation")

      opt[Unit]("overwrite")
        .action((_, c) => c.copy(overwrite = true))
        .text("Overwrite CPG if it already exists")

      opt[Unit]("store")
        .action((_, c) => c.copy(store = true))
        .text("Store graph changes made by bundle")

      note("REST server mode")

      opt[Unit]("server")
        .action((_, c) => c.copy(server = true))
        .text("run as HTTP server")

      opt[String]("server-host")
        .action((x, c) => c.copy(serverHost = x))
        .text("Hostname on which to expose the CPGQL server")

      opt[Int]("server-port")
        .action((x, c) => c.copy(serverPort = x))
        .text("Port on which to expose the CPGQL server")

      opt[String]("server-auth-username")
        .action((x, c) => c.copy(serverAuthUsername = x))
        .text("Basic auth username for the CPGQL server")

      opt[String]("server-auth-password")
        .action((x, c) => c.copy(serverAuthPassword = x))
        .text("Basic auth password for the CPGQL server")

      note("Misc")

      opt[Unit]("nocolors")
        .action((_, c) => c.copy(nocolors = true))
        .text("turn off colors")

      help("help")
        .text("Print this help text")
    }

    // note: if config is really `None` an error message would have been displayed earlier
    parser.parse(args, Config()).get
  }

  protected def runAmmonite(config: Config, slProduct: SLProduct = OcularProduct): Unit = {
    if (config.listPlugins) {
      listPluginsAndLayerCreators(config)
    } else if (config.addPlugin.isDefined) {
      new PluginManager(InstallConfig().rootPath).add(config.addPlugin.get)
    } else if (config.rmPlugin.isDefined) {
      new PluginManager(InstallConfig().rootPath).rm(config.rmPlugin.get)
    } else {
      config.scriptFile match {
        case None =>
          if (config.server) {
            startHttpServer(config)
          } else if (config.pluginToRun.isDefined) {
            runPlugin(config)
          } else {
            startInteractiveShell(config, slProduct)
          }
        case Some(scriptFile) =>
          runScript(scriptFile, config)
      }
    }
  }

  private def listPluginsAndLayerCreators(config: Config): Unit = {
    println("Installed plugins:")
    println("==================")
    new PluginManager(InstallConfig().rootPath).listPlugins().foreach(println)
    println("Available layer creators")
    println()
    val code =
      """
        |println(run)
        |
        |""".stripMargin
    withTemporaryScript(code) { file =>
      runScript(os.Path(file.path.toString), config)
    }
  }

  private def withTemporaryScript(code: String)(f: File => Unit): Unit = {
    File.usingTemporaryDirectory("joern-bundle") { dir =>
      val file = (dir / "script.sc")
      file.write(code)
      f(file)
    }
  }

  private def runPlugin(config: Config): Unit = {
    if (config.src.isEmpty) {
      println("You must supply a source directory with the --src flag")
      return
    }

    val bundleName = config.pluginToRun.get
    val src = better.files.File(config.src.get).path.toAbsolutePath.toString
    val language = config.language.getOrElse(io.shiftleft.console.cpgcreation.guessLanguage(src).getOrElse("c"))
    val storeCode = if (config.store) { "save" } else { "" }
    val code = s"""
        | if (${config.overwrite} || !workspace.projectExists("$src")) {
        |   workspace.projects
        |   .filter(_.inputPath == "$src")
        |   .map(_.name).foreach(n => workspace.removeProject(n))
        |   importCode.$language("$src")
        |   run.ossdataflow
        |   save
        | } else {
        |    println("Using existing CPG - Use `--overwrite` if this is not what you want")
        |    workspace.projects
        |    .filter(x => x.inputPath == "$src")
        |    .map(_.name).map(open)
        | }
        | run.$bundleName
        | $storeCode
        |""".stripMargin

    val logFileName = "/tmp/joern-scan-log.txt"
    println(s"Detailed logs at: $logFileName")
    val file = new java.io.File(logFileName);
    val fos = new FileOutputStream(file);
    val ps = new PrintStream(fos);
    System.setErr(ps)
    withTemporaryScript(code) { file =>
      runScript(os.Path(file.path.toString), config)
    }
    ps.close()
  }

  private def startInteractiveShell(config: Config, slProduct: SLProduct) = {
    val configurePPrinterMaybe =
      if (config.nocolors) ""
      else """val originalPPrinter = repl.pprinter()
             |repl.pprinter.update(io.shiftleft.console.pprinter.create(originalPPrinter))
             |""".stripMargin

    val replConfig = List(
      "repl.prompt() = \"" + promptStr() + "\"",
      configurePPrinterMaybe,
      "banner()"
    )
    ammonite
      .Main(
        predefCode = predefPlus(additionalImportCode(config) ++ replConfig ++ shutdownHooks),
        welcomeBanner = None,
        storageBackend = new StorageBackend(slProduct),
        remoteLogging = false,
        colors = ammoniteColors(config)
      )
      .run()
  }

  private def startHttpServer(config: Config): Unit = {
    val predef = predefPlus(additionalImportCode(config))
    val ammonite = new EmbeddedAmmonite(predef)
    ammonite.start()
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      ammonite.shutdown()
    }))
    val server = new CPGQLServer(ammonite,
                                 config.serverHost,
                                 config.serverPort,
                                 config.serverAuthUsername,
                                 config.serverAuthPassword)
    server.main(Array.empty)
  }

  private def runScript(scriptFile: Path, config: Config) = {
    val isEncryptedScript = scriptFile.ext == "enc"
    System.err.println(s"executing $scriptFile with params=${config.params}")
    val scriptArgs: Seq[String] = {
      val commandArgs = config.command.toList
      val parameterArgs = config.params.flatMap { case (key, value) => Seq(s"--$key", value) }
      commandArgs ++ parameterArgs
    }
    val actualScriptFile =
      if (isEncryptedScript) decryptedScript(scriptFile)
      else scriptFile
    ammonite
      .Main(
        predefCode = predefPlus(additionalImportCode(config) ++ shutdownHooks),
        remoteLogging = false,
        colors = ammoniteColors(config)
      )
      .runScript(actualScriptFile, scriptArgs)
      ._1 match {
      case Res.Success(r) =>
        System.err.println(s"script finished successfully")
        System.err.println(r)
      case Res.Failure(msg) =>
        throw new AssertionError(s"script failed: $msg")
      case Res.Exception(e, msg) =>
        throw new AssertionError(s"script errored: $msg", e)
      case _ => ???
    }
    /* minimizing exposure time by deleting the decrypted script straight away */
    if (isEncryptedScript) actualScriptFile.toIO.delete
  }

  private def additionalImportCode(config: Config): List[String] =
    config.additionalImports.flatMap { importScript =>
      val file = importScript.toIO
      assert(file.canRead, s"unable to read $file")
      readScript(file.toScala)
    }

  private def ammoniteColors(config: Config) =
    if (config.nocolors) Colors.BlackWhite
    else Colors.Default

  /**
    * Override this method to implement script decryption
    * */
  protected def decryptedScript(scriptFile: Path): Path = {
    scriptFile
  }

  private def readScript(scriptFile: File): List[String] = {
    val code = scriptFile.lines.toList
    println(s"importing $scriptFile (${code.size} lines)")
    code
  }

  protected def predefPlus(lines: List[String]): String

  protected def shutdownHooks: List[String]

  protected def promptStr(): String

}
