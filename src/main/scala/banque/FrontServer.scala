package banque

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import petri._

import java.io.InputStream
import java.net.{InetSocketAddress, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Interface web locale de demonstration.
 *
 * Lancement:
 *   sbt "runMain banque.FrontServer 8080"
 */
object FrontServer {
  private implicit val timeout: Timeout = Timeout(3.seconds)
  private val defaultAccounts = List(
    "ACC-001" -> 1000.0,
    "ACC-002" -> 500.0,
    "ACC-003" -> 750.0
  )

  @volatile private var bankSystem: ActorSystem[Banque.CommandeBanque] =
    ActorSystem(Banque(), s"FrontBankSystem${System.currentTimeMillis()}")

  def main(args: Array[String]): Unit = {
    val port = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    seedDefaultAccounts()

    val server = HttpServer.create(new InetSocketAddress("localhost", port), 0)
    server.createContext("/", handle)
    server.setExecutor(Executors.newCachedThreadPool())
    server.start()

    sys.addShutdownHook {
      server.stop(0)
      bankSystem.terminate()
    }

    println(s"Interface front disponible sur http://localhost:$port")
    new CountDownLatch(1).await()
  }

  private def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)

    if (exchange.getRequestMethod == "OPTIONS") {
      respond(exchange, 204, "", "text/plain")
      return
    }

    try {
      val path = exchange.getRequestURI.getPath
      (exchange.getRequestMethod, path) match {
        case ("GET", "/") => serveResource(exchange, "/front/index.html", "text/html")
        case ("GET", "/app.js") => serveResource(exchange, "/front/app.js", "application/javascript")
        case ("GET", "/styles.css") => serveResource(exchange, "/front/styles.css", "text/css")

        case ("GET", "/api/state") => respondJson(exchange, stateJson())
        case ("GET", "/api/petri") => respondJson(exchange, petriAnalysisJson())
        case ("GET", "/api/ltl") =>
          val query = parseQuery(exchange)
          val formula = query.getOrElse("formula", "G enabled")
          respondJson(exchange, ltlJson(formula))
        case ("GET", "/api/history") =>
          val query = parseQuery(exchange)
          respondJson(exchange, historyJson(query.getOrElse("accountId", "")))

        case ("POST", "/api/reset") =>
          resetBank()
          respondJson(exchange, stateJson())
        case ("POST", "/api/create") =>
          val form = parseForm(exchange)
          val result = askResponse(replyTo =>
            Banque.CreerCompteReq(form.getOrElse("accountId", ""), form.double("soldeInitial"), replyTo)
          )
          respondJson(exchange, envelopeJson(result, stateJson()))
        case ("POST", "/api/deposit") =>
          val form = parseForm(exchange)
          val result = askResponse(replyTo =>
            Banque.OperationCompteBanque(Deposer(form.getOrElse("accountId", ""), form.double("montant"), replyTo))
          )
          respondJson(exchange, envelopeJson(result, stateJson()))
        case ("POST", "/api/withdraw") =>
          val form = parseForm(exchange)
          val result = askResponse(replyTo =>
            Banque.OperationCompteBanque(Retirer(form.getOrElse("accountId", ""), form.double("montant"), replyTo))
          )
          respondJson(exchange, envelopeJson(result, stateJson()))
        case ("POST", "/api/transfer") =>
          val form = parseForm(exchange)
          val result = askResponse(replyTo =>
            Banque.OperationCompteBanque(
              Virement(
                form.getOrElse("source", ""),
                form.double("montant"),
                form.getOrElse("destination", ""),
                bankSystem.ignoreRef,
                replyTo
              )
            )
          )
          respondJson(exchange, envelopeJson(result, stateJson()))
        case ("POST", "/api/close") =>
          val form = parseForm(exchange)
          val result = askResponse(replyTo =>
            Banque.OperationCompteBanque(FermerCompte(form.getOrElse("accountId", ""), replyTo))
          )
          respondJson(exchange, envelopeJson(result, stateJson()))
        case ("POST", "/api/delete") =>
          val form = parseForm(exchange)
          val result = askResponse(replyTo =>
            Banque.SupprimerCompteReq(form.getOrElse("accountId", ""), replyTo)
          )
          respondJson(exchange, envelopeJson(result, stateJson()))

        case _ =>
          respond(exchange, 404, "Not found", "text/plain")
      }
    } catch {
      case e: Exception =>
        respondJson(exchange, s"""{"ok":false,"error":${quote(e.getMessage)}}""", 500)
    }
  }

  private def resetBank(): Unit = synchronized {
    val accounts = askBank[Banque.ListeComptes](Banque.ListerComptesReq)
    accounts.comptes.keys.foreach { accountId =>
      askResponse(replyTo => Banque.SupprimerCompteReq(accountId, replyTo))
    }
    seedDefaultAccounts()
  }

  private def seedDefaultAccounts(): Unit =
    defaultAccounts.foreach { case (accountId, solde) =>
      askResponse(replyTo => Banque.CreerCompteReq(accountId, solde, replyTo))
    }

  private def askBank[A](makeCommand: ActorRef[A] => Banque.CommandeBanque): A = {
    val system = bankSystem
    implicit val scheduler = system.scheduler
    Await.result(system.ask[A](makeCommand), timeout.duration + 1.second)
  }

  private def askResponse(makeCommand: ActorRef[ReponseBancaire] => Banque.CommandeBanque): ReponseBancaire =
    askBank[ReponseBancaire](makeCommand)

  private def stateJson(): String = {
    val accounts = askBank[Banque.ListeComptes](Banque.ListerComptesReq)
    val global = askBank[Banque.SoldeGlobal](Banque.ConsulterSoldeGlobalReq)
    val accountJson = accounts.comptes.toList.sortBy(_._1).map { case (id, solde) =>
      s"""{"id":${quote(id)},"solde":$solde}"""
    }.mkString("[", ",", "]")

    s"""{"ok":true,"accounts":$accountJson,"total":${global.total},"count":${global.nombreComptes}}"""
  }

  private def historyJson(accountId: String): String = {
    val result = askResponse(replyTo =>
      Banque.OperationCompteBanque(ConsulterHistorique(accountId, replyTo))
    )

    result match {
      case HistoriqueCompte(id, transactions) =>
        val entries = transactions.map { tx =>
          s"""{"id":${quote(tx.id.value)},"type":${quote(tx.type_.toString)},"montant":${tx.montant},"soldeApres":${tx.soldeApres},"description":${quote(tx.description)}}"""
        }.mkString("[", ",", "]")
        s"""{"ok":true,"accountId":${quote(id)},"transactions":$entries}"""
      case other =>
        responseJson(other)
    }
  }

  private def petriAnalysisJson(): String = {
    val net = BankingPetriNet.createCompleteNet(defaultAccounts.map(_._1))
    val checker = new PropertyChecker(net)
    val (reachable, _) = net.getReachabilityGraph
    val properties = List(
      checker.checkNoDeadlock,
      checker.checkLiveness,
      checker.checkBoundedness(),
      checker.checkInvariant("Marquages non negatifs", _.tokens.values.forall(_ >= 0))
    )

    val ltlFormulas = List(
      "G enabled",
      "G !deadlock",
      "G (has_ACC-001_available | has_ACC-001_locked)",
      "G (has_ACC-002_available | has_ACC-002_locked)"
    )
    val ltlResults = new LTLModelChecker(net).checkAll(ltlFormulas)

    s"""{"ok":true,"places":${net.places.size},"transitions":${net.transitions.size},"arcs":${net.arcs.size},"states":${reachable.size},"properties":${propertyResultsJson(properties)},"ltl":${ltlResultsJson(ltlResults)}}"""
  }

  private def ltlJson(formula: String): String = {
    val net = BankingPetriNet.createCompleteNet(defaultAccounts.map(_._1))
    val result = new LTLModelChecker(net).check(formula)
    s"""{"ok":true,"result":${ltlResultJson(result)}}"""
  }

  private def envelopeJson(result: ReponseBancaire, state: String): String =
    s"""{"ok":${isSuccess(result)},"response":${responseJson(result)},"state":$state}"""

  private def responseJson(response: ReponseBancaire): String = response match {
    case OperationReussie(transactionId, nouveauSolde, timestamp) =>
      s"""{"type":"OperationReussie","transactionId":${quote(transactionId.value)},"nouveauSolde":$nouveauSolde,"timestamp":$timestamp}"""
    case OperationEchouee(raison, timestamp) =>
      s"""{"type":"OperationEchouee","raison":${quote(raison)},"timestamp":$timestamp}"""
    case SoldeActuel(accountId, solde, timestamp) =>
      s"""{"type":"SoldeActuel","accountId":${quote(accountId)},"solde":$solde,"timestamp":$timestamp}"""
    case HistoriqueCompte(accountId, transactions) =>
      s"""{"type":"HistoriqueCompte","accountId":${quote(accountId)},"transactions":${transactions.size}}"""
    case VirementEnvoyeAvecConfirmation(transactionId, montant, nouveauSolde, confirmationDestination, timestamp) =>
      s"""{"type":"VirementEnvoyeAvecConfirmation","transactionId":${quote(transactionId.value)},"montant":$montant,"nouveauSolde":$nouveauSolde,"confirmationDestination":$confirmationDestination,"timestamp":$timestamp}"""
    case product: Product if product.productPrefix.startsWith("CompteCre") =>
      val accountId = product.productElement(0).asInstanceOf[String]
      val solde = product.productElement(1).asInstanceOf[Double]
      val timestamp = product.productElement(2).asInstanceOf[Long]
      s"""{"type":"CompteCree","accountId":${quote(accountId)},"solde":$solde,"timestamp":$timestamp}"""
    case product: Product if product.productPrefix.startsWith("CompteFerm") =>
      val accountId = product.productElement(0).asInstanceOf[String]
      val soldeRestitue = product.productElement(1).asInstanceOf[Double]
      val timestamp = product.productElement(2).asInstanceOf[Long]
      s"""{"type":"CompteFerme","accountId":${quote(accountId)},"soldeRestitue":$soldeRestitue,"timestamp":$timestamp}"""
    case other =>
      s"""{"type":${quote(other.getClass.getSimpleName)}}"""
  }

  private def isSuccess(response: ReponseBancaire): Boolean = response match {
    case _: OperationEchouee => false
    case _ => true
  }

  private def propertyResultsJson(results: List[PropertyCheckResult]): String =
    results.map { result =>
      s"""{"property":${quote(result.property)},"valid":${result.isValid},"message":${quote(result.message)},"details":${result.details.map(quote).getOrElse("null")}}"""
    }.mkString("[", ",", "]")

  private def ltlResultsJson(results: List[LTLVerificationResult]): String =
    results.map(ltlResultJson).mkString("[", ",", "]")

  private def ltlResultJson(result: LTLVerificationResult): String = {
    val counter = result.counterExample
      .map(_.map(marking => quote(marking.toString)).mkString("[", ",", "]"))
      .getOrElse("null")
    s"""{"formula":${quote(result.formula)},"valid":${result.isValid},"message":${quote(result.message)},"counterExample":$counter}"""
  }

  private def serveResource(exchange: HttpExchange, resource: String, contentType: String): Unit = {
    val stream = Option(getClass.getResourceAsStream(resource))
    stream match {
      case Some(input) =>
        try respond(exchange, 200, readAll(input), s"$contentType; charset=utf-8")
        finally input.close()
      case None =>
        respond(exchange, 404, "Resource not found", "text/plain")
    }
  }

  private def parseForm(exchange: HttpExchange): Map[String, String] =
    parseEncoded(readAll(exchange.getRequestBody))

  private def parseQuery(exchange: HttpExchange): Map[String, String] =
    Option(exchange.getRequestURI.getRawQuery).map(parseEncoded).getOrElse(Map.empty)

  private def parseEncoded(encoded: String): Map[String, String] =
    encoded.split("&").toList.filter(_.nonEmpty).flatMap { pair =>
      val parts = pair.split("=", 2)
      if (parts.length == 2) {
        Some(decode(parts(0)) -> decode(parts(1)))
      } else {
        None
      }
    }.toMap

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())

  private def readAll(input: InputStream): String =
    new String(input.readAllBytes(), StandardCharsets.UTF_8)

  private def respondJson(exchange: HttpExchange, body: String, status: Int = 200): Unit =
    respond(exchange, status, body, "application/json; charset=utf-8")

  private def respond(exchange: HttpExchange, status: Int, body: String, contentType: String): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", contentType)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val response = exchange.getResponseBody
    try response.write(bytes)
    finally response.close()
  }

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
    headers.set("Access-Control-Allow-Headers", "Content-Type")
  }

  private def quote(value: String): String =
    "\"" + value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c => c.toString
    } + "\""

  private implicit class RichForm(private val form: Map[String, String]) extends AnyVal {
    def double(key: String): Double =
      form.get(key).flatMap(value => Try(value.toDouble).toOption).getOrElse(0.0)
  }
}
