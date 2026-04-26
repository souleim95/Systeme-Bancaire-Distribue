package petri

// ============ FORMULES LTL ============

sealed trait LTLFormula {
  override def toString: String = this match {
    case Atom(name) => name
    case Not(f) => s"not($f)"
    case And(f1, f2) => s"($f1 ∧ $f2)"
    case Or(f1, f2) => s"($f1 ∨ $f2)"
    case Next(f) => s"X($f)"
    case Finally(f) => s"F($f)"
    case Globally(f) => s"G($f)"
    case Until(f1, f2) => s"($f1 U $f2)"
    case Release(f1, f2) => s"($f1 R $f2)"
  }
}

case class Atom(name: String) extends LTLFormula
case class Not(formula: LTLFormula) extends LTLFormula
case class And(left: LTLFormula, right: LTLFormula) extends LTLFormula
case class Or(left: LTLFormula, right: LTLFormula) extends LTLFormula
case class Next(formula: LTLFormula) extends LTLFormula
case class Finally(formula: LTLFormula) extends LTLFormula
case class Globally(formula: LTLFormula) extends LTLFormula
case class Until(left: LTLFormula, right: LTLFormula) extends LTLFormula
case class Release(left: LTLFormula, right: LTLFormula) extends LTLFormula

// ============ PARSEUR LTL ============

class LTLParser(input: String) {
  private val tokens = tokenize(input)
  private var pos = 0

  private def tokenize(input: String): List[String] = {
    val tokenPattern = """->|[()!&|]|[A-Za-z_][A-Za-z0-9_\-]*|\S""".r
    tokenPattern.findAllIn(input).map {
      case "!" => "NOT"
      case "&" => "AND"
      case "|" => "OR"
      case "(" => "LPAREN"
      case ")" => "RPAREN"
      case "->" => "IMPLIES"
      case token => token
    }.toList
  }

  def parse(): LTLFormula = {
    val formula = parseImplication()
    if (pos < tokens.length) {
      throw new ParseException(s"Tokens non consommes: ${tokens.drop(pos).mkString(" ")}")
    }
    formula
  }

  private def parseImplication(): LTLFormula = {
    var left = parseOr()
    while (accept("IMPLIES")) {
      val right = parseImplication()
      left = Or(Not(left), right)
    }
    left
  }

  private def parseOr(): LTLFormula = {
    var left = parseAnd()
    while (accept("OR")) {
      val right = parseAnd()
      left = Or(left, right)
    }
    left
  }

  private def parseAnd(): LTLFormula = {
    var left = parseUntilRelease()
    while (accept("AND")) {
      val right = parseUntilRelease()
      left = And(left, right)
    }
    left
  }

  private def parseUntilRelease(): LTLFormula = {
    var left = parseUnary()
    while (current.contains("U") || current.contains("R")) {
      val op = tokens(pos)
      pos += 1
      val right = parseUnary()
      left = if (op == "U") Until(left, right) else Release(left, right)
    }
    left
  }

  private def parseUnary(): LTLFormula = {
    current match {
      case Some("NOT") =>
        pos += 1
        Not(parseUnary())
      case Some("X" | "NEXT") =>
        pos += 1
        Next(parseUnary())
      case Some("F" | "FINALLY") =>
        pos += 1
        Finally(parseUnary())
      case Some("G" | "GLOBALLY") =>
        pos += 1
        Globally(parseUnary())
      case _ =>
        parsePrimary()
    }
  }

  private def parsePrimary(): LTLFormula = {
    current match {
      case None =>
        throw new ParseException("Fin inattendue de l'entree")
      case Some("LPAREN") =>
        pos += 1
        val formula = parseImplication()
        if (!accept("RPAREN")) {
          throw new ParseException("Parenthese fermante manquante")
        }
        formula
      case Some(token) if reserved(token) =>
        throw new ParseException(s"Token inattendu: $token")
      case Some(atom) =>
        pos += 1
        Atom(atom)
    }
  }

  private def current: Option[String] =
    if (pos < tokens.length) Some(tokens(pos)) else None

  private def accept(token: String): Boolean = {
    if (current.contains(token)) {
      pos += 1
      true
    } else {
      false
    }
  }

  private def reserved(token: String): Boolean =
    Set("RPAREN", "AND", "OR", "IMPLIES", "U", "R").contains(token)
}

class ParseException(message: String) extends Exception(message)

object LTLParser {
  def parse(formula: String): LTLFormula = {
    try {
      new LTLParser(formula).parse()
    } catch {
      case e: ParseException => throw e
      case e: Exception => throw new ParseException(s"Erreur parsing LTL: ${e.getMessage}")
    }
  }
}

// ============ EVALUATEUR LTL ============

class LTLEvaluator(petriNet: PetriNet) {
  def verify(formula: LTLFormula): Boolean = {
    val (_, transitions) = petriNet.getReachabilityGraph
    val paths = generatePathsFrom(petriNet.initialMarking, transitions, maxDepth = 100)
    paths.forall(path => evaluatePath(formula, path))
  }

  private def evaluatePath(formula: LTLFormula, path: List[Marking]): Boolean = {
    def eval(f: LTLFormula, index: Int): Boolean = f match {
      case Atom(name) => evaluateAtom(name, path(index % path.length))
      case Not(inner) => !eval(inner, index)
      case And(left, right) => eval(left, index) && eval(right, index)
      case Or(left, right) => eval(left, index) || eval(right, index)
      case Next(inner) => eval(inner, index + 1)
      case Finally(inner) =>
        (index until (index + path.length)).exists(i => eval(inner, i))
      case Globally(inner) =>
        (index until (index + path.length)).forall(i => eval(inner, i))
      case Until(left, right) =>
        (index until (index + path.length)).exists { j =>
          eval(right, j) && (index until j).forall(i => eval(left, i))
        }
      case Release(left, right) =>
        (index until (index + path.length)).forall { j =>
          eval(right, j) || (index until j).exists(i => eval(left, i))
        }
    }

    path.nonEmpty && eval(formula, 0)
  }

  private def evaluateAtom(name: String, marking: Marking): Boolean = name match {
    case "true" => true
    case "false" => false
    case prop if prop.startsWith("has_") =>
      marking(prop.substring(4)) > 0
    case prop if prop.startsWith("count_") =>
      val parts = prop.substring(6).split("_eq_")
      parts.length == 2 && parts(1).toIntOption.exists(marking(parts(0)) == _)
    case prop =>
      marking(prop) > 0
  }

  private def generatePathsFrom(
    marking: Marking,
    transitions: Map[Marking, Map[String, Marking]],
    maxDepth: Int
  ): List[List[Marking]] = {
    def buildPaths(current: Marking, depth: Int, visited: Set[Marking]): List[List[Marking]] = {
      if (depth == 0 || visited.contains(current)) {
        List(List(current))
      } else {
        val nextStates = transitions.getOrElse(current, Map.empty)
        if (nextStates.isEmpty) {
          List(List(current))
        } else {
          nextStates.values.toList.flatMap { next =>
            buildPaths(next, depth - 1, visited + current).map(current :: _)
          }
        }
      }
    }

    buildPaths(marking, maxDepth, Set.empty)
  }
}

object LTLEvaluator {
  def apply(petriNet: PetriNet): LTLEvaluator = new LTLEvaluator(petriNet)
}

// ============ MODEL CHECKER ============

case class LTLVerificationResult(
  formula: String,
  isValid: Boolean,
  message: String,
  counterExample: Option[List[Marking]] = None
) {
  override def toString: String = {
    val status = if (isValid) "VALID" else "INVALID"
    val base = s"[$status] LTL Formula: $formula\n  Message: $message"

    counterExample match {
      case Some(path) if path.nonEmpty =>
        base + "\n  Counter-example path:\n" +
          path.zipWithIndex.map { case (m, i) => s"    Step $i: $m" }.mkString("\n")
      case _ => base
    }
  }
}

class LTLModelChecker(petriNet: PetriNet) {
  def check(formulaString: String): LTLVerificationResult = {
    try {
      val formula = LTLParser.parse(formulaString)
      val isValid = LTLEvaluator(petriNet).verify(formula)

      LTLVerificationResult(
        formula = formulaString,
        isValid = isValid,
        message = if (isValid) "Formula is satisfied on all paths" else "Formula is violated on some path"
      )
    } catch {
      case e: ParseException =>
        LTLVerificationResult(formulaString, isValid = false, s"Parse error: ${e.getMessage}")
      case e: Exception =>
        LTLVerificationResult(formulaString, isValid = false, s"Verification error: ${e.getMessage}")
    }
  }

  def checkAll(formulas: List[String]): List[LTLVerificationResult] =
    formulas.map(check)

  def printReport(results: List[LTLVerificationResult]): Unit = {
    println("\n" + "=" * 70)
    println("LTL MODEL CHECKING RESULTS")
    println("=" * 70)

    results.zipWithIndex.foreach { case (result, idx) =>
      println(s"\n${idx + 1}. ${result.formula}")
      println(s"   Status: ${if (result.isValid) "PASS" else "FAIL"}")
      println(s"   ${result.message}")
    }

    val passed = results.count(_.isValid)
    println(s"\n${"-" * 70}")
    println(s"Summary: $passed/${results.size} formulas verified")
    println("=" * 70)
  }
}

// ============ EXEMPLES DE PROPRIETES LTL ============

object LTLProperties {
  object Banking {
    val accountAvailability = "G (has_accountAvailable_p)"
    val depositCompletion = "F (has_transferCompleted_p)"
    val transferGuarantee = "G (has_transferInitiated_p -> F (has_transferCompleted_p))"
    val noDeadlock = "F true"
    val accountValid = "G (has_sourceAvailable_p | has_destAvailable_p)"
  }

  object Safety {
    val noInvalidState = "G !false"
    val resourceConservation = "G true"
  }

  object Liveness {
    val eventuallyHappens = "F true"
    val alwaysCanProgress = "G true"
  }
}
