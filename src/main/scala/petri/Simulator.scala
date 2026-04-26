package petri

import scala.collection.mutable
import scala.io.StdIn

/**
 * Trace d'exécution d'une transition
 */
case class ExecutionStep(
  transitionId: String,
  transitionLabel: String,
  fromMarking: Marking,
  toMarking: Marking,
  timestamp: Long = System.currentTimeMillis()
) {
  override def toString: String = {
    s"$transitionLabel: $fromMarking -> $toMarking"
  }
}

/**
 * Simulateur interactif du réseau de Pétri
 */
class Simulator(petriNet: PetriNet) {
  
  private var executionTrace: List[ExecutionStep] = List()
  private var currentMarking: Marking = petriNet.initialMarking
  
  /**
   * Afficher l'état courant
   */
  def displayCurrentState(): Unit = {
    println(s"\nÉtat courant: $currentMarking")
    
    val enabledTrans = petriNet.getEnabledTransitions(currentMarking)
    if (enabledTrans.isEmpty) {
      println("Aucune transition activée (Deadlock?)")
    } else {
      println(s"Transitions activées: ${enabledTrans.size}")
      enabledTrans.zipWithIndex.foreach { case (transId, idx) =>
        val label = petriNet.transitions(transId).label
        println(s"  ${idx + 1}. $label ($transId)")
      }
    }
  }
  
  /**
   * Exécuter une transition
   */
  def executeTransition(transId: String): Boolean = {
    petriNet.fireTransition(transId, currentMarking) match {
      case Some(newMarking) =>
        val step = ExecutionStep(
          transId,
          petriNet.transitions(transId).label,
          currentMarking,
          newMarking
        )
        executionTrace = executionTrace :+ step
        currentMarking = newMarking
        println(s"✓ Transition exécutée: ${step.transitionLabel}")
        true
      case None =>
        println(s"✗ La transition $transId ne peut pas être exécutée")
        false
    }
  }
  
  /**
   * Afficher l'historique d'exécution
   */
  def displayTrace(): Unit = {
    if (executionTrace.isEmpty) {
      println("Aucune exécution enregistrée")
      return
    }
    
    println("\n" + "="*60)
    println("TRACE D'EXÉCUTION")
    println("="*60)
    println(s"État initial: ${petriNet.initialMarking}")
    
    executionTrace.zipWithIndex.foreach { case (step, idx) =>
      println(s"${idx + 1}. ${step.transitionLabel}")
      println(s"   Avant:  ${step.fromMarking}")
      println(s"   Après:  ${step.toMarking}")
    }
    
    println(s"\nÉtat final: $currentMarking")
    println("="*60)
  }
  
  /**
   * Réinitialiser la simulation
   */
  def reset(): Unit = {
    currentMarking = petriNet.initialMarking
    executionTrace = List()
    println("Simulation réinitialisée")
  }
  
  /**
   * Mode simulation interactive
   */
  def interactiveMode(): Unit = {
    println("\n" + "="*60)
    println("SIMULATION INTERACTIVE DU RÉSEAU DE PÉTRI")
    println("="*60)
    
    var running = true
    
    while (running) {
      displayCurrentState()
      
      println("\nCommandes: (1-n) exécuter transition, 'h' historique, 'r' réinitialiser, 'q' quitter")
      print("> ")
      
      val input = scala.io.StdIn.readLine().trim.toLowerCase
      
      input match {
        case "h" => displayTrace()
        case "r" => reset()
        case "q" => 
          running = false
          println("Simulation terminée")
        case _ =>
          try {
            val choice = input.toInt
            val enabledTrans = petriNet.getEnabledTransitions(currentMarking)
            
            if (choice >= 1 && choice <= enabledTrans.size) {
              val transId = enabledTrans(choice - 1)
              executeTransition(transId)
            } else {
              println("Choix invalide")
            }
          } catch {
            case _: NumberFormatException =>
              println("Entrée invalide")
          }
      }
    }
  }
  
  /**
   * Simulation automatique avec une séquence aléatoire
   */
  def randomSimulation(maxSteps: Int = 100): Unit = {
    println("\n" + "="*60)
    println("SIMULATION ALÉATOIRE")
    println("="*60)
    println(s"État initial: $currentMarking\n")
    
    var step = 0
    val rand = scala.util.Random
    
    while (step < maxSteps) {
      val enabledTrans = petriNet.getEnabledTransitions(currentMarking)
      
      if (enabledTrans.isEmpty) {
        println(s"Deadlock atteint après {step} étapes")
        println(s"État final: $currentMarking")
        return
      }
      
      val randomTrans = enabledTrans(rand.nextInt(enabledTrans.size))
      executeTransition(randomTrans)
      step += 1
    }
    
    println(s"\nSimulation complétée après $step étapes")
    println(s"État final: $currentMarking")
  }
  
  /**
   * Exécuter une séquence de transitions
   */
  def executeSequence(sequence: List[String]): Boolean = {
    for (transId <- sequence) {
      if (!petriNet.transitions.contains(transId)) {
        println(s"Transition inconnue: $transId")
        return false
      }
      
      if (!executeTransition(transId)) {
        println(s"Impossible d'exécuter la séquence à partir de: $transId")
        return false
      }
    }
    
    println("Séquence exécutée avec succès")
    true
  }
  
  /**
   * Afficher les statistiques
   */
  def displayStatistics(): Unit = {
    println("\n" + "="*60)
    println("STATISTIQUES DE SIMULATION")
    println("="*60)
    println(f"Étapes exécutées: ${executionTrace.size}")
    println(f"Durée totale: ${if (executionTrace.isEmpty) 0 else executionTrace.last.timestamp - executionTrace.head.timestamp}ms")
    
    val transitionCounts = executionTrace.groupBy(_.transitionId).view.mapValues(_.size)
    println("\nTransitions exécutées:")
    for ((transId, count) <- transitionCounts.toList.sortBy(-_._2)) {
      val label = petriNet.transitions(transId).label
      println(s"  $label: $count fois")
    }
    
    println("="*60)
  }
}

object Simulator {
  def apply(petriNet: PetriNet): Simulator = new Simulator(petriNet)
}
