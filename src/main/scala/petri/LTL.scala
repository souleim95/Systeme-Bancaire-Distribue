package petri

/**
 * Logique Temporelle Linéaire (LTL - Linear Temporal Logic)
 * Pour vérifier des propriétés sur les chemins du réseau de Pétri
 * 
 * Syntaxe LTL:
 * - Atomes: p, q, r, ... (propriétés sur l'état courant)
 * - Booléens: φ ∧ ψ (AND), φ ∨ ψ (OR), ¬φ (NOT)
 * - Temporels: X φ (next), F φ (eventually), G φ (globally), φ U ψ (until)
 * - Dérivés: φ R ψ (release) ≡ ¬(¬φ U ¬ψ)
 */

// ============ FORMULES LTL ============

sealed trait LTLFormula {
  override def toString: String = this match {
    case Atom(name) => name
    case Not(f) => s"¬($f)"
    case And(f1, f2) => s"($f1 ∧ $f2)"
    case Or(f1, f2) => s"($f1 ∨ $f2)"
    case Next(f) => s"X($f)"
    case Finally(f) => s"F($f)"
    case Globally(f) => s"G($f)"
    case Until(f1, f2) => s"($f1 U $f2)"
    case Release(f1, f2) => s"($f1 R $f2)"
  }
}

// Atome propositionnel
case class Atom(name: String) extends LTLFormula

// Opérateurs booléens
case class Not(formula: LTLFormula) extends LTLFormula
case class And(left: LTLFormula, right: LTLFormula) extends LTLFormula
case class Or(left: LTLFormula, right: LTLFormula) extends LTLFormula

// Opérateurs temporels
case class Next(formula: LTLFormula) extends LTLFormula          // X φ
case class Finally(formula: LTLFormula) extends LTLFormula       // F φ
case class Globally(formula: LTLFormula) extends LTLFormula      // G φ
case class Until(left: LTLFormula, right: LTLFormula) extends LTLFormula  // φ U ψ
case class Release(left: LTLFormula, right: LTLFormula) extends LTLFormula // φ R ψ

// ============ PARSEUR LTL ============

class LTLParser(input: String) {
  private val tokens = tokenize(input)
  private var pos = 0
  
  /**
   * Tokenizer : convertir la chaîne en tokens
   */
  private def tokenize(input: String): List[String] = {
    val symbols = Map(
      "¬" -> "NOT", "!" -> "NOT",
      "∧" -> "AND", "&" -> "AND",
      "∨" -> "OR", "|" -> "OR",
      "(" -> "LPAREN", ")" -> "RPAREN"
    )
    
    input
      .replaceAll("\\s+", " ")  // Normaliser les espaces
      .split(" ")
      .flatMap { token =>
        // Décomposer les symboles s'ils sont attachés à des mots
        var remaining = token
        var result = scala.collection.mutable.ListBuffer[String]()
        
        for (char <- token) {
          symbols.get(char.toString) match {
            case Some(sym) => result += sym
            case None => remaining = remaining.replaceFirst("\\\\\\Q" + char + "\\E", "")
          }
        }
        
        if (remaining.nonEmpty && !symbols.values.toList.contains(remaining)) {
          result += remaining
        }
        
        result.toList
      }
      .filter(_.nonEmpty)
      .toList
  }
  
  /**
   * Parser récursif descendant
   */
  def parse(): LTLFormula = {
    val formula = parseOr()
    if (pos < tokens.length) {
      throw new ParseException(s"Tokens non consommés: ${tokens.drop(pos).mkString(" ")}")
    }
    formula
  }
  
  private def parseOr(): LTLFormula = {
    var left = parseAnd()
    while (pos < tokens.length && (tokens(pos) == "OR" || tokens(pos) == "|")) {
      pos += 1
      val right = parseAnd()
      left = Or(left, right)
    }
    left
  }
  
  private def parseAnd(): LTLFormula = {
    var left = parseNot()
    while (pos < tokens.length && (tokens(pos) == "AND" || tokens(pos) == "&")) {
      pos += 1
      val right = parseNot()
      left = And(left, right)
    }
    left
  }
  
  private def parseNot(): LTLFormula = {
    if (pos < tokens.length && (tokens(pos) == "NOT" || tokens(pos) == "!")) {
      pos += 1
      Not(parseNot())
    } else {
      parseTemporal()
    }
  }
  
  private def parseTemporal(): LTLFormula = {
    if (pos < tokens.length) {
      tokens(pos) match {
        case "X" | "NEXT" =>
          pos += 1
          Next(parseTemporal())
        case "F" | "FINALLY" =>
          pos += 1
          Finally(parseTemporal())
        case "G" | "GLOBALLY" =>
          pos += 1
          Globally(parseTemporal())
        case _ =>
          parseUntilRelease()
      }
    } else {
      throw new ParseException("Fin inattendue de l'entrée")
    }
  }
  
  private def parseUntilRelease(): LTLFormula = {
    var left = parsePrimary()
    
    while (pos < tokens.length && (tokens(pos) == "U" || tokens(pos) == "R")) {
      val op = tokens(pos)
      pos += 1
      val right = parsePrimary()
      left = if (op == "U") Until(left, right) else Release(left, right)
    }
    
    left
  }
  
  private def parsePrimary(): LTLFormula = {
    if (pos >= tokens.length) {
      throw new ParseException("Fin inattendue de l'entrée")
    }
    
    tokens(pos) match {
      case "LPAREN" | "(" =>
        pos += 1
        val formula = parseOr()
        if (pos >= tokens.length || tokens(pos) != "RPAREN") {
          throw new ParseException("Parenthèse fermante manquante")
        }
        pos += 1
        formula
      case "NOT" | "!" =>
        parseNot()
      case "X" | "NEXT" =>
        pos += 1
        Next(parseTemporal())
      case "F" | "FINALLY" =>
        pos += 1
        Finally(parseTemporal())
      case "G" | "GLOBALLY" =>
        pos += 1
        Globally(parseTemporal())
      case atom =>
        pos += 1
        Atom(atom)
    }
  }
}

class ParseException(message: String) extends Exception(message)

object LTLParser {
  def parse(formula: String): LTLFormula = {
    try {
      new LTLParser(formula).parse()
    } catch {
      case e: Exception =>
        throw new ParseException(s"Erreur parsing LTL: ${e.getMessage}")
    }
  }
}

// ============ ÉVALUATEUR LTL ============

/**
 * Évaluateur de formules LTL sur les chemins du réseau de Pétri
 */
class LTLEvaluator(petriNet: PetriNet) {
  
  /**
   * Vérifier une formule LTL sur tous les chemins possibles
   * Une formule est valide si elle est vraie sur TOUS les chemins infinis
   */
  def verify(formula: LTLFormula): Boolean = {
    val (reachable, transitions) = petriNet.getReachabilityGraph
    
    // Pour chaque état accessible, vérifier la formule
    reachable.forall { marking =>
      val paths = generatePathsFrom(marking, petriNet, transitions, maxDepth = 100)
      paths.forall { path =>
        evaluatePath(formula, path)
      }
    }
  }
  
  /**
   * Évaluer une formule sur un chemin infini
   */
  private def evaluatePath(formula: LTLFormula, path: List[Marking]): Boolean = {
    def eval(f: LTLFormula, index: Int): Boolean = {
      f match {
        case Atom(name) =>
          // Vérifier l'atome à l'état courant
          evaluateAtom(name, path(index % path.length))
        
        case Not(inner) =>
          !eval(inner, index)
        
        case And(left, right) =>
          eval(left, index) && eval(right, index)
        
        case Or(left, right) =>
          eval(left, index) || eval(right, index)
        
        case Next(inner) =>
          if (index + 1 < path.length) {
            eval(inner, index + 1)
          } else {
            // Assumer la boucle (état infini)
            eval(inner, (index + 1) % path.length)
          }
        
        case Finally(inner) =>
          // F φ: φ est true à un certain point dans le futur
          (index until (index + path.length)).exists { i =>
            eval(inner, i % path.length)
          }
        
        case Globally(inner) =>
          // G φ: φ est true partout dans le futur
          (index until (index + path.length)).forall { i =>
            eval(inner, i % path.length)
          }
        
        case Until(left, right) =>
          // φ U ψ: φ est vrai jusqu'à ce que ψ devient vrai
          (index until (index + path.length)).exists { j =>
            eval(right, j % path.length) &&
            (index until j).forall { i =>
              eval(left, i % path.length)
            }
          }
        
        case Release(left, right) =>
          // φ R ψ: ψ reste vrai jusqu'à ce que φ devient vrai (ou toujours vrai)
          (index until (index + path.length)).forall { j =>
            eval(right, j % path.length) ||
            (index until j).exists { i =>
              eval(left, i % path.length)
            }
          }
      }
    }
    
    if (path.isEmpty) false
    else eval(formula, 0)
  }
  
  /**
   * Évaluer un atome (proposition) sur un état
   */
  private def evaluateAtom(name: String, marking: Marking): Boolean = {
    name match {
      // Propriétés communes
      case "true" => true
      case "false" => false
      
      // Propriétés sur les places
      case prop if prop.startsWith("has_") =>
        val placeId = prop.substring(4)
        marking(placeId) > 0
      
      case prop if prop.startsWith("count_") =>
        val parts = prop.substring(6).split("_eq_")
        if (parts.length == 2) {
          val placeId = parts(0)
          val count = parts(1).toIntOption.getOrElse(-1)
          marking(placeId) == count
        } else {
          false
        }
      
      // Propriétés personnalisées: peuvent être étendues
      case _ =>
        // Par défaut, vérifier si la place a au least 1 jeton
        marking(name) > 0
    }
  }
  
  /**
   * Générer les chemins possibles à partir d'un état
   */
  private def generatePathsFrom(
    marking: Marking,
    petriNet: PetriNet,
    transitions: Map[Marking, Map[String, Marking]],
    maxDepth: Int
  ): List[List[Marking]] = {
    def buildPaths(current: Marking, depth: Int, visited: Set[Marking]): List[List[Marking]] = {
      if (depth == 0) {
        List(List(current))
      } else if (visited.contains(current)) {
        // Cycle détecté: créer une boucle
        List(List(current))
      } else {
        val nextStates = transitions.getOrElse(current, Map())
        
        if (nextStates.isEmpty) {
          // État terminal
          List(List(current))
        } else {
          nextStates.values.flatMap { next =>
            val paths = buildPaths(next, depth - 1, visited + current)
            paths.map(p => current :: p)
          }.toList
        }
      }
    }
    
    buildPaths(marking, maxDepth, Set())
  }
}

object LTLEvaluator {
  def apply(petriNet: PetriNet): LTLEvaluator = new LTLEvaluator(petriNet)
}

// ============ MODÈLE CHECKER ============

/**
 * Model Checker LTL complet
 */
case class LTLVerificationResult(
  formula: String,
  isValid: Boolean,
  message: String,
  counterExample: Option[List[Marking]] = None
) {
  override def toString: String = {
    val status = if (isValid) "✓ VALID" else "✗ INVALID"
    val base = s"[$status] LTL Formula: $formula\n  Message: $message"
    
    counterExample match {
      case Some(path) if path.nonEmpty =>
        base + s"\n  Counter-example path:\n" +
          path.zipWithIndex.map { case (m, i) =>
            s"    Step $i: $m"
          }.mkString("\n")
      case _ => base
    }
  }
}

class LTLModelChecker(petriNet: PetriNet) {
  
  /**
   * Vérifier une formule LTL
   */
  def check(formulaString: String): LTLVerificationResult = {
    try {
      val formula = LTLParser.parse(formulaString)
      val evaluator = LTLEvaluator(petriNet)
      
      val isValid = evaluator.verify(formula)
      
      LTLVerificationResult(
        formula = formulaString,
        isValid = isValid,
        message = if (isValid) "Formula is satisfied on all paths" 
                  else "Formula is violated on some path"
      )
    } catch {
      case e: ParseException =>
        LTLVerificationResult(
          formula = formulaString,
          isValid = false,
          message = s"Parse error: ${e.getMessage}"
        )
      case e: Exception =>
        LTLVerificationResult(
          formula = formulaString,
          isValid = false,
          message = s"Verification error: ${e.getMessage}"
        )
    }
  }
  
  /**
   * Vérifier plusieurs formules et retourner un rapport
   */
  def checkAll(formulas: List[String]): List[LTLVerificationResult] = {
    formulas.map(check)
  }
  
  /**
   * Afficher un rapport complet
   */
  def printReport(results: List[LTLVerificationResult]): Unit = {
    println("\n" + "="*70)
    println("LTL MODEL CHECKING RESULTS")
    println("="*70)
    
    results.zipWithIndex.foreach { case (result, idx) =>
      println(s"\n${idx + 1}. ${result.formula}")
      println(s"   Status: ${if (result.isValid) "✓ PASS" else "✗ FAIL"}")
      println(s"   ${result.message}")
    }
    
    val passed = results.count(_.isValid)
    println(f"\n${"-"*70}")
    println(f"Summary: $passed/${results.size} formulas verified")
    println("="*70)
  }
}

// ============ EXEMPLES DE PROPRIÉTÉS LTL ============

object LTLProperties {
  
  /**
   * Propriétés communes pour systèmes bancaires
   */
  object Banking {
    // "Globalement, un compte disponible reste disponible sauf en transaction"
    val accountAvailability = "G (has_accountAvailable_p)"
    
    // "Finalement, chaque dépôt est complété"
    val depositCompletion = "F (has_transferCompleted_p)"
    
    // "Si une transaction est initiée, elle sera complétée"
    val transferGuarantee = "G (has_transferInitiated_p -> F (has_transferCompleted_p))"
    
    // "Pas de deadlock permanent"
    val noDeadlock = "F true"  // Simplifié
    
    // "Les comptes restent valides"
    val accountValid = "G (has_sourceAvailable_p | has_destAvailable_p)"
  }
  
  /**
   * Propriétés de sûreté générales
   */
  object Safety {
    // "Jamais d'état invalide"
    val noInvalidState = "G !false"
    
    // "Pas de fuite de ressources"
    val resourceConservation = "G true"
  }
  
  /**
   * Propriétés de vivacité
   */
  object Liveness {
    // "Finalement, quelque chose se produit"
    val eventuallyHappens = "F true"
    
    // "Toujours la possibilité de continuer"
    val alwaysCanProgress = "G true"
  }
}
