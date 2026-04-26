package petri

/**
 * Vérificateur de propriétés du réseau de Pétri
 * Détecte les deadlocks, les livelocks et vérifie les invariants
 */
case class PropertyCheckResult(
  isValid: Boolean,
  property: String,
  message: String,
  details: Option[String] = None
) {
  override def toString: String = {
    val status = if (isValid) "✓ PASS" else "✗ FAIL"
    s"[$status] $property: $message" + details.map(d => s"\n  Details: $d").getOrElse("")
  }
}

class PropertyChecker(petriNet: PetriNet) {
  
  /**
   * Vérifier l'absence de deadlocks
   */
  def checkNoDeadlock: PropertyCheckResult = {
    val (reachable, _) = petriNet.getReachabilityGraph
    
    // Vérifier si un état terminal (pas de transitions possibles) existe
    val deadlockStates = reachable.filter { marking =>
      petriNet.getEnabledTransitions(marking).isEmpty
    }
    
    if (deadlockStates.isEmpty) {
      PropertyCheckResult(
        isValid = true,
        property = "No Deadlock",
        message = "Pas de deadlock détecté dans le réseau"
      )
    } else {
      PropertyCheckResult(
        isValid = false,
        property = "No Deadlock",
        message = s"${deadlockStates.size} états de deadlock détectés",
        details = Some(deadlockStates.map(_.toString).mkString(", "))
      )
    }
  }
  
  /**
   * Vérifier la vivacité : chaque transition peut à un moment être exécutée
   */
  def checkLiveness: PropertyCheckResult = {
    val (reachable, transitions) = petriNet.getReachabilityGraph
    
    val unexecutableTransitions = petriNet.transitions.keys.filter { transId =>
      reachable.exists(marking => !canReachTransition(marking, transId, transitions))
    }.toList
    
    if (unexecutableTransitions.isEmpty) {
      PropertyCheckResult(
        isValid = true,
        property = "Liveness",
        message = "Toutes les transitions restent vivantes depuis chaque etat atteignable"
      )
    } else {
      PropertyCheckResult(
        isValid = false,
        property = "Liveness",
        message = s"${unexecutableTransitions.size} transition(s) ne sont pas vivantes",
        details = Some(unexecutableTransitions.mkString(", "))
      )
    }
  }
  
  /**
   * Vérifier que le marquage est borné
   */
  def checkBoundedness(bound: Int = 10): PropertyCheckResult = {
    val (reachable, _) = petriNet.getReachabilityGraph
    
    val unboundedPlaces = petriNet.places.keys.filter { placeId =>
      val maxTokens = reachable.map(_(placeId)).max
      maxTokens > bound
    }.toList
    
    if (unboundedPlaces.isEmpty) {
      PropertyCheckResult(
        isValid = true,
        property = s"Boundedness (limit=$bound)",
        message = s"Toutes les places sont bornées par $bound"
      )
    } else {
      PropertyCheckResult(
        isValid = false,
        property = s"Boundedness (limit=$bound)",
        message = s"Places non bornées détectées: ${unboundedPlaces.mkString(", ")}",
        details = Some(unboundedPlaces.map { p =>
          val maxTokens = reachable.map(_(p)).max
          s"$p: max=$maxTokens"
        }.mkString(", "))
      )
    }
  }
  
  /**
   * Vérifier que le marquage initial est atteignable depuis tout état
   */
  def checkReversibility: PropertyCheckResult = {
    val (reachable, transitions) = petriNet.getReachabilityGraph
    
    // Pour chaque état atteignable, vérifier si on peut revenir à l'initial
    var canReturnToInitial = true
    var problemStates: List[Marking] = List()
    
    for (marking <- reachable) {
      if (!canReachMarking(marking, petriNet.initialMarking, transitions)) {
        canReturnToInitial = false
        problemStates = problemStates :+ marking
      }
    }
    
    if (canReturnToInitial) {
      PropertyCheckResult(
        isValid = true,
        property = "Reversibility",
        message = "Le marquage initial est atteignable depuis tout état"
      )
    } else {
      PropertyCheckResult(
        isValid = false,
        property = "Reversibility",
        message = s"${problemStates.size} états ne peuvent pas revenir au marquage initial",
        details = Some(problemStates.take(3).map(_.toString).mkString(", "))
      )
    }
  }
  
  /**
   * Vérifier un invariant personnalisé
   */
  def checkInvariant(name: String, predicate: Marking => Boolean): PropertyCheckResult = {
    val (reachable, _) = petriNet.getReachabilityGraph
    
    val isValid = reachable.forall(predicate)
    
    if (isValid) {
      PropertyCheckResult(
        isValid = true,
        property = name,
        message = s"L'invariant $name est satisfait dans tous les états"
      )
    } else {
      val counterExamples = reachable.filter(!predicate(_)).take(3)
      PropertyCheckResult(
        isValid = false,
        property = name,
        message = s"L'invariant $name est violé",
        details = Some(s"Contre-exemples: ${counterExamples.map(_.toString).mkString(", ")}")
      )
    }
  }
  
  /**
   * Vérifier que le solde ne peut jamais être négatif
   * Pour un réseau bancaire
   */
  def checkNoNegativeBalance(balancePlaces: Map[String, String]): PropertyCheckResult = {
    def hasNegativeBalance(marking: Marking): Boolean = {
      balancePlaces.values.exists { placeId =>
        marking(placeId) < 0
      }
    }
    
    checkInvariant(
      "No Negative Balance",
      m => !hasNegativeBalance(m)
    )
  }
  
  /**
   * Analyser complètement le réseau
   */
  def analyzeNetwork: List[PropertyCheckResult] = {
    List(
      checkNoDeadlock,
      checkLiveness,
      checkBoundedness(),
      checkReversibility
    )
  }
  
  /**
   * Montrer un résumé complet
   */
  def printAnalysis(): Unit = {
    println("\n" + "="*60)
    println("ANALYSE DU RÉSEAU DE PÉTRI")
    println("="*60)
    println(s"Réseau: ${petriNet.places.size} places, ${petriNet.transitions.size} transitions")
    
    val (reachable, _) = petriNet.getReachabilityGraph
    println(s"Espace d'états: ${reachable.size} états atteignables")
    
    println("\n" + "-"*60)
    println("RÉSULTATS DES VÉRIFICATIONS")
    println("-"*60)
    
    analyzeNetwork.foreach { result =>
      println(result)
      println()
    }
    
    println("="*60)
  }
  
  // ========== UTILITIES ==========
  
  private def canReachMarking(
    from: Marking, 
    to: Marking, 
    transitions: Map[Marking, Map[String, Marking]]
  ): Boolean = {
    val visited = scala.collection.mutable.Set[Marking]()
    val queue = scala.collection.mutable.Queue[Marking]()
    
    queue.enqueue(from)
    visited.add(from)
    
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (current == to) return true
      
      transitions.get(current).foreach { reachableStates =>
        reachableStates.foreach { case (_, nextMarking) =>
          if (!visited.contains(nextMarking)) {
            visited.add(nextMarking)
            queue.enqueue(nextMarking)
          }
        }
      }
    }
    
    false
  }

  private def canReachTransition(
    from: Marking,
    transitionId: String,
    transitions: Map[Marking, Map[String, Marking]]
  ): Boolean = {
    val visited = scala.collection.mutable.Set[Marking]()
    val queue = scala.collection.mutable.Queue[Marking]()

    queue.enqueue(from)
    visited.add(from)

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (petriNet.isTransitionEnabled(transitionId, current)) return true

      transitions.get(current).foreach { reachableStates =>
        reachableStates.values.foreach { nextMarking =>
          if (!visited.contains(nextMarking)) {
            visited.add(nextMarking)
            queue.enqueue(nextMarking)
          }
        }
      }
    }

    false
  }
}
