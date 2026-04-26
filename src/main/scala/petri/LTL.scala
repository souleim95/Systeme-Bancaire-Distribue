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
    val tokenPattern = """->|→|[()!&|¬∧∨]|[A-Za-z_][A-Za-z0-9_\-]*|\S""".r
    tokenPattern.findAllIn(input).map {
      case "!" | "¬" => "NOT"
      case "&" | "∧" => "AND"
      case "|" | "∨" => "OR"
      case "(" => "LPAREN"
      case ")" => "RPAREN"
      case "->" | "→" => "IMPLIES"
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
  private case class LassoPath(states: Vector[Marking], loopStart: Int) {
    require(states.nonEmpty, "A path must contain at least one state")
    require(loopStart >= 0 && loopStart < states.length, "Invalid loop start")

    def nextIndex(index: Int): Int =
      if (index + 1 < states.length) index + 1 else loopStart

    def finiteHorizonFrom(index: Int): Vector[Int] = {
      val prefix = index until states.length
      val loop = loopStart until states.length
      (prefix ++ loop).toVector.distinct
    }

    def asCounterExample: List[Marking] =
      (states :+ states(loopStart)).toList
  }

  def verify(formula: LTLFormula): Boolean =
    counterExample(formula).isEmpty

  def counterExample(formula: LTLFormula): Option[List[Marking]] = {
    val (reachable, transitions) = petriNet.getReachabilityGraph
    fastCounterExample(formula, reachable, transitions).getOrElse {
      generateLassoPaths(petriNet.initialMarking, transitions)
        .find(path => !evaluatePath(formula, path))
        .map(_.asCounterExample)
    }
  }

  private def fastCounterExample(
    formula: LTLFormula,
    reachable: Set[Marking],
    transitions: Map[Marking, Map[String, Marking]]
  ): Option[Option[List[Marking]]] = formula match {
    case f if statePredicate(f).isDefined =>
      val predicate = statePredicate(f).get
      Some(if (predicate(petriNet.initialMarking)) None else Some(List(petriNet.initialMarking)))

    case Globally(inner) if statePredicate(inner).isDefined =>
      val predicate = statePredicate(inner).get
      val violation = reachable.find(marking => !predicate(marking))
      Some(violation.map(marking => pathTo(marking, transitions).getOrElse(List(marking))))

    case Finally(inner) if statePredicate(inner).isDefined =>
      val predicate = statePredicate(inner).get
      Some(findAvoidingCounterExample(petriNet.initialMarking, predicate, transitions))

    case Globally(Or(Not(condition), Finally(goal)))
        if statePredicate(condition).isDefined && statePredicate(goal).isDefined =>
      val conditionPredicate = statePredicate(condition).get
      val goalPredicate = statePredicate(goal).get
      val violation = reachable.toList.view.flatMap { marking =>
        if (conditionPredicate(marking)) {
          findAvoidingCounterExample(marking, goalPredicate, transitions).map { suffix =>
            val prefix = pathTo(marking, transitions).getOrElse(List(marking))
            prefix.dropRight(1) ++ suffix
          }
        } else {
          None
        }
      }.headOption
      Some(violation)

    case _ =>
      None
  }

  private def evaluatePath(formula: LTLFormula, path: LassoPath): Boolean = {
    def eval(f: LTLFormula, index: Int): Boolean = f match {
      case Atom(name) => evaluateAtom(name, path.states(index))
      case Not(inner) => !eval(inner, index)
      case And(left, right) => eval(left, index) && eval(right, index)
      case Or(left, right) => eval(left, index) || eval(right, index)
      case Next(inner) => eval(inner, path.nextIndex(index))
      case Finally(inner) =>
        path.finiteHorizonFrom(index).exists(i => eval(inner, i))
      case Globally(inner) =>
        path.finiteHorizonFrom(index).forall(i => eval(inner, i))
      case Until(left, right) =>
        path.finiteHorizonFrom(index).exists { j =>
          eval(right, j) && positionsUntil(path, index, j).forall(i => eval(left, i))
        }
      case Release(left, right) =>
        !eval(Until(Not(left), Not(right)), index)
    }

    eval(formula, 0)
  }

  private def positionsUntil(path: LassoPath, from: Int, to: Int): Vector[Int] = {
    val positions = scala.collection.mutable.ArrayBuffer[Int]()
    var current = from
    var guard = 0
    val maxSteps = path.states.length * 2 + 1

    while (current != to && guard < maxSteps) {
      positions += current
      current = path.nextIndex(current)
      guard += 1
    }

    positions.toVector
  }

  private def evaluateAtom(name: String, marking: Marking): Boolean = name match {
    case "true" => true
    case "false" => false
    case "enabled" | "has_enabled_transition" =>
      petriNet.getEnabledTransitions(marking).nonEmpty
    case "deadlock" =>
      petriNet.getEnabledTransitions(marking).isEmpty
    case prop if prop.startsWith("has_") =>
      marking(prop.substring(4)) > 0
    case prop if prop.startsWith("count_") =>
      evaluateCountAtom(prop.substring(6), marking)
    case prop =>
      marking(prop) > 0
  }

  private def statePredicate(formula: LTLFormula): Option[Marking => Boolean] = formula match {
    case Atom(name) =>
      Some(marking => evaluateAtom(name, marking))
    case Not(inner) =>
      statePredicate(inner).map(predicate => marking => !predicate(marking))
    case And(left, right) =>
      for {
        leftPredicate <- statePredicate(left)
        rightPredicate <- statePredicate(right)
      } yield marking => leftPredicate(marking) && rightPredicate(marking)
    case Or(left, right) =>
      for {
        leftPredicate <- statePredicate(left)
        rightPredicate <- statePredicate(right)
      } yield marking => leftPredicate(marking) || rightPredicate(marking)
    case _ =>
      None
  }

  private def pathTo(
    target: Marking,
    transitions: Map[Marking, Map[String, Marking]]
  ): Option[List[Marking]] = {
    val visited = scala.collection.mutable.Set[Marking](petriNet.initialMarking)
    val previous = scala.collection.mutable.Map[Marking, Marking]()
    val queue = scala.collection.mutable.Queue[Marking](petriNet.initialMarking)

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (current == target) {
        var path = List(current)
        var cursor = current
        while (previous.contains(cursor)) {
          cursor = previous(cursor)
          path = cursor :: path
        }
        return Some(path)
      }

      transitions.getOrElse(current, Map.empty).values.foreach { next =>
        if (!visited.contains(next)) {
          visited.add(next)
          previous(next) = current
          queue.enqueue(next)
        }
      }
    }

    None
  }

  private def findAvoidingCounterExample(
    start: Marking,
    goalPredicate: Marking => Boolean,
    transitions: Map[Marking, Map[String, Marking]]
  ): Option[List[Marking]] = {
    if (goalPredicate(start)) return None

    val colors = scala.collection.mutable.Map[Marking, String]()

    def dfs(current: Marking, stack: Vector[Marking]): Option[List[Marking]] = {
      if (goalPredicate(current)) return None

      colors(current) = "visiting"
      val newStack = stack :+ current
      val allNextStates = transitions.getOrElse(current, Map.empty).values.toList.distinct
      val nextStates = allNextStates.filterNot(goalPredicate)

      val result =
        if (allNextStates.isEmpty) {
          Some((newStack :+ current).toList)
        } else if (nextStates.isEmpty) {
          None
        } else {
          nextStates.iterator.flatMap { next =>
            colors.get(next) match {
              case Some("visiting") =>
                val loopStart = newStack.indexOf(next)
                Some((newStack.drop(loopStart) :+ next).toList)
              case Some("done") =>
                None
              case _ =>
                dfs(next, newStack)
            }
          }.toSeq.headOption
        }

      if (result.isEmpty) colors(current) = "done"
      result
    }

    dfs(start, Vector.empty)
  }

  private def evaluateCountAtom(expression: String, marking: Marking): Boolean = {
    val operators = List("_eq_" -> ((a: Int, b: Int) => a == b),
                         "_gte_" -> ((a: Int, b: Int) => a >= b),
                         "_lte_" -> ((a: Int, b: Int) => a <= b))

    operators.exists { case (separator, predicate) =>
      val parts = expression.split(separator)
      parts.length == 2 && parts(1).toIntOption.exists(limit => predicate(marking(parts(0)), limit))
    }
  }

  private def generateLassoPaths(
    initial: Marking,
    transitions: Map[Marking, Map[String, Marking]]
  ): List[LassoPath] = {
    def dfs(current: Marking, stack: Vector[Marking]): List[LassoPath] = {
      val nextStates = transitions.getOrElse(current, Map.empty).values.toList.distinct

      if (nextStates.isEmpty) {
        List(LassoPath(stack, stack.length - 1))
      } else {
        nextStates.flatMap { next =>
          val existingIndex = stack.indexOf(next)
          if (existingIndex >= 0) {
            List(LassoPath(stack, existingIndex))
          } else {
            dfs(next, stack :+ next)
          }
        }
      }
    }

    dfs(initial, Vector(initial))
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
      val evaluator = LTLEvaluator(petriNet)
      val counterExample = evaluator.counterExample(formula)
      val isValid = counterExample.isEmpty

      LTLVerificationResult(
        formula = formulaString,
        isValid = isValid,
        message = if (isValid) "Formula is satisfied on all paths" else "Formula is violated on some path",
        counterExample = counterExample
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
    val accountAvailability = "G (has_accountAvailable_p | has_accountLocked_p)"
    val depositCompletion = "F (has_transferCompleted_p)"
    val transferGuarantee = "G (has_transferInitiated_p -> F (has_transferCompleted_p | has_sourceAvailable_p))"
    val noDeadlock = "G enabled"
    val accountValid = "G (has_sourceAvailable_p | has_destAvailable_p)"
  }

  object Safety {
    val noInvalidState = "G !false"
    val resourceConservation = "G true"
  }

  object Liveness {
    val eventuallyHappens = "F true"
    val alwaysCanProgress = "G enabled"
  }
}
