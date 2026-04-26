package petri

import scala.collection.mutable

// ============ ÉLÉMENTS DE BASE DU RÉSEAU DE PÉTRI ============

/**
 * Place : nœud du réseau qui contient des jetons
 */
case class Place(
  id: String,
  label: String,
  initialMarking: Int = 0
) {
  override def toString: String = s"Place($label, marking=$initialMarking)"
}

/**
 * Transition : événement/action qui consomme et produit des jetons
 */
case class Transition(
  id: String,
  label: String,
  condition: Option[String] = None  // Condition pour activer la transition
) {
  override def toString: String = s"Transition($label)"
}

/**
 * Arc : connexion entre place et transition ou transition et place
 * weight : nombre de jetons consommés/produits
 */
case class Arc(
  source: String,      // ID de la place ou transition source
  target: String,      // ID de la place ou transition cible
  weight: Int = 1,
  isInhibitor: Boolean = false  // Arc inhibiteur (bloque si jetons présents)
) {
  override def toString: String = {
    val arcType = if (isInhibitor) "Inhibitor" else "Standard"
    s"Arc($source -> $target, weight=$weight, type=$arcType)"
  }
}

/**
 * Marquage : état du réseau (nombre de jetons dans chaque place)
 */
case class Marking(tokens: Map[String, Int]) {
  def apply(placeId: String): Int = tokens.getOrElse(placeId, 0)
  
  def update(placeId: String, count: Int): Marking = {
    Marking(tokens.updated(placeId, math.max(0, count)))
  }
  
  def increment(placeId: String, count: Int): Marking = {
    val current = tokens.getOrElse(placeId, 0)
    Marking(tokens.updated(placeId, current + count))
  }
  
  def decrement(placeId: String, count: Int): Marking = {
    val current = tokens.getOrElse(placeId, 0)
    val newCount = math.max(0, current - count)
    if (newCount == 0) Marking(tokens - placeId)
    else Marking(tokens.updated(placeId, newCount))
  }
  
  override def toString: String = {
    tokens.filter(_._2 > 0).map { case (placeId, count) =>
      s"$placeId:$count"
    }.mkString("{", ", ", "}")
  }
  
  override def equals(obj: Any): Boolean = obj match {
    case other: Marking => this.tokens == other.tokens
    case _ => false
  }
  
  override def hashCode(): Int = tokens.hashCode()
}

/**
 * Réseau de Pétri : structure complète
 */
class PetriNet(
  val places: Map[String, Place],
  val transitions: Map[String, Transition],
  val arcs: List[Arc],
  val initialMarking: Marking
) {
  
  // Index pour accès rapide
  private val incomingArcs: Map[String, List[Arc]] = arcs.groupBy(_.target)
  private val outgoingArcs: Map[String, List[Arc]] = arcs.groupBy(_.source)
  
  /**
   * Obtenir les transitions activées pour un marquage donné
   */
  def getEnabledTransitions(marking: Marking): List[String] = {
    transitions.keys.filter(transId => isTransitionEnabled(transId, marking)).toList
  }
  
  /**
   * Vérifier si une transition peut être activée
   */
  def isTransitionEnabled(transId: String, marking: Marking): Boolean = {
    if (!transitions.contains(transId)) return false
    
    val inArcs = incomingArcs.getOrElse(transId, List())
    
    // Vérifier les arcs précédents (places -> transition)
    val placeArcs = inArcs.filter(arc => places.contains(arc.source))
    
    for (arc <- placeArcs) {
      val tokens = marking(arc.source)
      if (arc.isInhibitor) {
        // Arc inhibiteur : bloque si des jetons sont présents
        if (tokens > 0) return false
      } else {
        // Arc normal : requiert au moins weight jetons
        if (tokens < arc.weight) return false
      }
    }
    
    true
  }
  
  /**
   * Appliquer une transition (passer au marquage suivant)
   */
  def fireTransition(transId: String, marking: Marking): Option[Marking] = {
    if (!isTransitionEnabled(transId, marking)) return None
    
    var newMarking = marking
    
    // Consommer les jetons des places d'entrée
    val inArcs = incomingArcs.getOrElse(transId, List())
    for (arc <- inArcs if places.contains(arc.source) && !arc.isInhibitor) {
      newMarking = newMarking.decrement(arc.source, arc.weight)
    }
    
    // Produire les jetons dans les places de sortie
    val outArcs = outgoingArcs.getOrElse(transId, List())
    for (arc <- outArcs if places.contains(arc.target)) {
      newMarking = newMarking.increment(arc.target, arc.weight)
    }
    
    Some(newMarking)
  }
  
  /**
   * Obtenir tous les marquages accessibles
   */
  def getReachabilityGraph: (Set[Marking], Map[Marking, Map[String, Marking]]) = {
    val reachable = mutable.Set[Marking]()
    val transitions_map = mutable.Map[Marking, Map[String, Marking]]()
    val queue = mutable.Queue[Marking]()
    
    queue.enqueue(initialMarking)
    reachable.add(initialMarking)
    
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      val nextStates = mutable.Map[String, Marking]()
      
      for (transId <- getEnabledTransitions(current)) {
        fireTransition(transId, current) match {
          case Some(nextMarking) =>
            nextStates(transId) = nextMarking
            if (!reachable.contains(nextMarking)) {
              reachable.add(nextMarking)
              queue.enqueue(nextMarking)
            }
          case None =>
        }
      }
      
      transitions_map(current) = nextStates.toMap
    }
    
    (reachable.toSet, transitions_map.toMap)
  }
  
  /**
   * Vérifier s'il existe un deadlock
   */
  def hasDeadlock: Boolean = {
    val (reachable, _) = getReachabilityGraph
    reachable.exists { marking =>
      getEnabledTransitions(marking).isEmpty
    }
  }
  
  /**
   * Vérifier une propriété invariant
   */
  def checkInvariant(predicate: Marking => Boolean): Boolean = {
    val (reachable, _) = getReachabilityGraph
    reachable.forall(predicate)
  }
  
  override def toString: String = {
    s"""PetriNet(
       |  Places: ${places.size}
       |  Transitions: ${transitions.size}
       |  Arcs: ${arcs.size}
       |  Initial Marking: $initialMarking
       |)""".stripMargin
  }
}

object PetriNet {
  def apply(
    places: Map[String, Place],
    transitions: Map[String, Transition],
    arcs: List[Arc],
    initialMarking: Marking
  ): PetriNet = new PetriNet(places, transitions, arcs, initialMarking)
}
