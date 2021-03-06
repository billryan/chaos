package mesosphere.chaos.http.impl

import java.io.File

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jetty9.InstrumentedHandler
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.{ ConstraintMapping, ConstraintSecurityHandler, MappedLoginService, LoginService }
import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler.{ ResourceHandler, RequestLogHandler, HandlerCollection }
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.security.{ Constraint, Password }
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.rogach.scallop.ScallopOption
import org.slf4j.LoggerFactory
import mesosphere.chaos.http.HttpConf

/**
  * Ensures the availability of a servlet context handler to the outside.
  * This handler is required in order to attach servlets and resources to it.
  */
trait ServletServer {
  def start()
  def stop()
  val servletHandler: ServletContextHandler
}

/**
  * Wraps up the Jetty server.
  */
class HttpServer(conf: HttpConf, registry: MetricRegistry) extends ServletServer {
  override val servletHandler: ServletContextHandler = servletContextHandler

  override def start(): Unit = httpServer.start()

  override def stop(): Unit = httpServer.stop()

  // TODO make configurable
  val welcomeFiles = Array("index.html")
  private[this] val log = LoggerFactory.getLogger(getClass.getName)

  protected val resourceCacheControlHeader: Option[String] = Some("max-age=0, must-revalidate")

  private lazy val httpServer: Server = {
    val server = new Server()

    val httpConfig = new HttpConfiguration()
    httpConfig.setSecureScheme("https")
    httpConfig.setSecurePort(conf.httpsPort())
    httpConfig.setOutputBufferSize(32768)

    def addConnector(name: String)(connector: Option[Connector]): Unit = {
      connector match {
        case Some(conn) =>
          log.info(s"Adding $name support.")
          server.addConnector(conn)
        case _ =>
          log.info(s"No $name support configured.")
      }
    }

    def getHTTPConnector(server: Server, httpConfig: HttpConfiguration): Option[ServerConnector] = {
      if (!conf.disableHttp()) {
        val connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig))
        configureConnectorAddress(connector, conf.httpAddress, conf.httpPort)
        Some(connector)
      }
      else {
        None
      }
    }

    def getHTTPSConnector(server: Server, httpConfig: HttpConfiguration): Option[ServerConnector] = {
      def createHTTPSConnector(keystorePath: String, keystorePassword: String): ServerConnector = {
        val keystore = new File(keystorePath)
        require(keystore.exists() && keystore.canRead,
          f"${conf.sslKeystorePath()} is invalid or not readable!")

        val contextFactory = new SslContextFactory()
        contextFactory.setKeyStorePath(keystorePath)
        contextFactory.setKeyStorePassword(keystorePassword)

        val sslConfig = new HttpConfiguration(httpConfig)
        sslConfig.addCustomizer(new SecureRequestCustomizer())

        val sslConnector = new ServerConnector(server, new SslConnectionFactory(contextFactory, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(sslConfig))
        configureConnectorAddress(sslConnector, conf.httpsAddress, conf.httpsPort)

        sslConnector
      }

      for {
        keystorePath <- conf.sslKeystorePath.get
        keystorePassword <- conf.sslKeystorePassword.get
        connector = createHTTPSConnector(keystorePath, keystorePassword)
      } yield connector
    }

    def configureConnectorAddress(connector: ServerConnector, addressOpt: ScallopOption[String], portOpt: ScallopOption[Int]): Unit = {
      connector.setIdleTimeout(30000)
      addressOpt.foreach(connector.setHost)
      portOpt.get match {
        case Some(port) =>
          connector.setPort(port)
        case None =>
          // shouldn't happen because our port configurations all have defaults
          throw new IllegalArgumentException("Port required.")
      }
    }

    val httpConnector: Option[Connector] = getHTTPConnector(server, httpConfig)
    addConnector("HTTP")(httpConnector)

    val httpsConnector: Option[Connector] = getHTTPSConnector(server, httpConfig)
    addConnector("HTTPS")(httpsConnector)

    // verify connector configuration
    (httpConnector, httpsConnector) match {
      case (Some(_), Some(_)) =>
        log.warn(s"Both HTTP and HTTPS support have been configured. " +
          s"Consider disabling HTTP with --${conf.disableHttp.name}")
      case (None, None) =>
        throw new IllegalArgumentException(
          "Invalid configuration: Neither HTTP nor HTTPS support has been configured.")
      case _ =>
      // everything seems fine
    }

    server.setHandler(handlerCollection)
    server
  }

  private lazy val instrumentedHandler: InstrumentedHandler = {
    val handler = new InstrumentedHandler(registry)
    handler.setHandler(servletContextHandler)
    handler
  }

  private lazy val handlerCollection: HandlerCollection = {
    val handlers = new HandlerCollection()
    handlers.setHandlers(Array(instrumentedHandler, resourceHandler, requestLogHandler))
    handlers
  }

  private lazy val requestLogHandler: RequestLogHandler = {
    val handler = new RequestLogHandler()
    handler.setRequestLog(requestLog)
    handler
  }

  private lazy val requestLog: RequestLog = new ChaosRequestLog

  private lazy val resourceHandler: ResourceHandler = {
    val handler = new ResourceHandler
    handler.setDirectoriesListed(false)
    resourceCacheControlHeader foreach handler.setCacheControl
    handler.setWelcomeFiles(welcomeFiles)
    handler.setResourceBase(conf.assetsUrl().toExternalForm)
    handler
  }

  private lazy val servletContextHandler: ServletContextHandler = {
    def createLoginService(userName: String, password: String): LoginService = {

      val loginService = new MappedLoginService() {
        override def loadUser(username: String): UserIdentity = null

        override def loadUsers(): Unit = {}

        override def getName: String = conf.httpCredentialsRealm()
      }

      //TODO(*): Use a MD5 instead.
      loginService.putUser(userName, new Password(password), Array("user"))
      loginService
    }

    def createSecurityHandler(httpCredentials: String): Option[ConstraintSecurityHandler] = {
      def createSecurityHandler(userName: String, password: String): ConstraintSecurityHandler = {

        val constraint = new Constraint(Constraint.__BASIC_AUTH, "user")
        constraint.setAuthenticate(true)

        //TODO(FL): Make configurable
        constraint.setRoles(Array("user", "admin"))

        // map the security constraint to the root path.
        val cm = new ConstraintMapping()
        cm.setConstraint(constraint)
        cm.setPathSpec("/*")

        // create the security handler, set the authentication to Basic
        // and assign the realm.
        val csh = new ConstraintSecurityHandler()
        csh.setAuthenticator(new BasicAuthenticator())
        csh.setRealmName(conf.httpCredentialsRealm())
        csh.addConstraintMapping(cm)
        csh.setLoginService(createLoginService(userName, password))

        csh
      }

      val credentialsPattern = "(.+):(.+)".r

      httpCredentials match {
        case credentialsPattern(userName, password) =>
          Option(createSecurityHandler(userName, password))
        case _ =>
          log.error(s"The HTTP credentials must be specified in the form of 'user:password'.")
          None
      }
    }

    val handler = new ServletContextHandler()

    conf.httpCredentials.get flatMap createSecurityHandler foreach handler.setSecurityHandler
    handler
  }
}
