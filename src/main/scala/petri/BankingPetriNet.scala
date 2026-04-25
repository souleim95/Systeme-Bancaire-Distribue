package petri

/**
 * Réseau de Pétri pour modéliser les opérations bancaires
 * 
 * Places:
 * - accountAvailable_p : Compte disponible pour transaction
 * - depositPending_p : Dépôt en attente
 * - withdrawValid_p : Retrait valide (solde suffisant)
 * - transferInitiated_p : Virement initié
 * - transferCompleted_p : Virement complété
 * - accountLocked_p : Compte verrouillé (pas d'opération concurrente)
 * 
 * Transitions:
 * - deposit_t : Effectuer un dépôt
 * - withdraw_t : Effectuer un retrait
 * - initiateTransfer_t : Initier un virement
 * - completeTransfer_t : Compléter un virement
 */
object BankingPetriNet {
  
  def createSingleAccountNet(): PetriNet = {
    // Définir les places
    val places = Map(
      "accountAvailable_p" -> Place("accountAvailable_p", "Account Available", 1),
      "depositPending_p" -> Place("depositPending_p", "Deposit Pending", 0),
      "withdrawValid_p" -> Place("withdrawValid_p", "Withdraw Valid", 0),
      "accountLocked_p" -> Place("accountLocked_p", "Account Locked", 0)
    )
    
    // Définir les transitions
    val transitions = Map(
      "deposit_t" -> Transition("deposit_t", "Deposit"),
      "withdraw_t" -> Transition("withdraw_t", "Withdraw"),
      "releaseAccount_t" -> Transition("releaseAccount_t", "Release Account")
    )
    
    // Définir les arcs
    val arcs = List(
      // Dépôt
      Arc("accountAvailable_p", "deposit_t", 1),
      Arc("deposit_t", "accountAvailable_p", 1),
      
      // Retrait
      Arc("accountAvailable_p", "withdraw_t", 1),
      Arc("withdrawValid_p", "withdraw_t", 1),
      Arc("withdraw_t", "accountAvailable_p", 1),
      
      // Libérer le compte
      Arc("accountLocked_p", "releaseAccount_t", 1),
      Arc("releaseAccount_t", "accountAvailable_p", 1)
    )
    
    // Marquage initial
    val initialMarking = Marking(Map("accountAvailable_p" -> 1))
    
    PetriNet(places, transitions, arcs, initialMarking)
  }
  
  /**
   * Réseau pour deux comptes avec virements
   */
  def createTransferNet(): PetriNet = {
    val places = Map(
      // Compte source
      "sourceAvailable_p" -> Place("sourceAvailable_p", "Source Account Available", 1),
      "sourceValid_p" -> Place("sourceValid_p", "Source Valid", 0),
      "sourceLocked_p" -> Place("sourceLocked_p", "Source Locked", 0),
      
      // Compte destination
      "destAvailable_p" -> Place("destAvailable_p", "Destination Account Available", 1),
      "destLocked_p" -> Place("destLocked_p", "Destination Locked", 0),
      
      // État du virement
      "transferInitiated_p" -> Place("transferInitiated_p", "Transfer Initiated", 0),
      "transferCompleted_p" -> Place("transferCompleted_p", "Transfer Completed", 0)
    )
    
    val transitions = Map(
      "initiateTransfer_t" -> Transition("initiateTransfer_t", "Initiate Transfer"),
      "completeTransfer_t" -> Transition("completeTransfer_t", "Complete Transfer"),
      "abortTransfer_t" -> Transition("abortTransfer_t", "Abort Transfer"),
      "releaseBothAccounts_t" -> Transition("releaseBothAccounts_t", "Release Both Accounts")
    )
    
    val arcs = List(
      // Initier le virement : source doit avoir des fonds, les deux comptes doivent être disponibles
      Arc("sourceAvailable_p", "initiateTransfer_t", 1),
      Arc("sourceValid_p", "initiateTransfer_t", 1),
      Arc("destAvailable_p", "initiateTransfer_t", 1),
      Arc("initiateTransfer_t", "sourceLocked_p", 1),
      Arc("initiateTransfer_t", "destLocked_p", 1),
      Arc("initiateTransfer_t", "transferInitiated_p", 1),
      
      // Compléter le virement
      Arc("transferInitiated_p", "completeTransfer_t", 1),
      Arc("sourceLocked_p", "completeTransfer_t", 1),
      Arc("destLocked_p", "completeTransfer_t", 1),
      Arc("completeTransfer_t", "transferCompleted_p", 1),
      Arc("completeTransfer_t", "sourceAvailable_p", 1),
      Arc("completeTransfer_t", "destAvailable_p", 1),
      
      // Annuler le virement
      Arc("transferInitiated_p", "abortTransfer_t", 1),
      Arc("sourceLocked_p", "abortTransfer_t", 1),
      Arc("destLocked_p", "abortTransfer_t", 1),
      Arc("abortTransfer_t", "sourceAvailable_p", 1),
      Arc("abortTransfer_t", "destAvailable_p", 1)
    )
    
    val initialMarking = Marking(Map(
      "sourceAvailable_p" -> 1,
      "destAvailable_p" -> 1
    ))
    
    PetriNet(places, transitions, arcs, initialMarking)
  }
  
  /**
   * Réseau complet pour n comptes avec toutes les opérations
   */
  def createCompleteNet(accountIds: List[String]): PetriNet = {
    require(accountIds.nonEmpty, "Au moins un compte doit être spécifié")
    
    var places = Map[String, Place]()
    var transitions = Map[String, Transition]()
    var arcs = List[Arc]()
    var initialMarkingMap = Map[String, Int]()
    
    // Créer les places pour chaque compte
    for (accountId <- accountIds) {
      places = places + (
        s"${accountId}_available" -> Place(s"${accountId}_available", s"$accountId Available", 1),
        s"${accountId}_valid" -> Place(s"${accountId}_valid", s"$accountId Valid", 0),
        s"${accountId}_locked" -> Place(s"${accountId}_locked", s"$accountId Locked", 0)
      )
      initialMarkingMap = initialMarkingMap + (s"${accountId}_available" -> 1)
    }
    
    // Créer les transitions pour chaque compte
    for (accountId <- accountIds) {
      transitions = transitions + (
        s"${accountId}_deposit_t" -> Transition(s"${accountId}_deposit_t", s"Deposit to $accountId"),
        s"${accountId}_withdraw_t" -> Transition(s"${accountId}_withdraw_t", s"Withdraw from $accountId"),
        s"${accountId}_release_t" -> Transition(s"${accountId}_release_t", s"Release $accountId")
      )
    }
    
    // Créer les arcs pour chaque compte
    for (accountId <- accountIds) {
      // Dépôt
      arcs = arcs :+ Arc(s"${accountId}_available", s"${accountId}_deposit_t", 1)
      arcs = arcs :+ Arc(s"${accountId}_deposit_t", s"${accountId}_available", 1)
      
      // Retrait
      arcs = arcs :+ Arc(s"${accountId}_available", s"${accountId}_withdraw_t", 1)
      arcs = arcs :+ Arc(s"${accountId}_valid", s"${accountId}_withdraw_t", 1)
      arcs = arcs :+ Arc(s"${accountId}_withdraw_t", s"${accountId}_available", 1)
      
      // Libérer le compte
      arcs = arcs :+ Arc(s"${accountId}_locked", s"${accountId}_release_t", 1)
      arcs = arcs :+ Arc(s"${accountId}_release_t", s"${accountId}_available", 1)
    }
    
    // Créer les transitions de virement entre chaque paire de comptes
    for (i <- 0 until accountIds.length; j <- 0 until accountIds.length if i != j) {
      val source = accountIds(i)
      val dest = accountIds(j)
      val transId = s"transfer_${source}_to_${dest}_t"
      
      transitions = transitions + (transId -> Transition(transId, s"Transfer $source -> $dest"))
      
      // Initier le virement
      arcs = arcs :+ Arc(s"${source}_available", transId, 1)
      arcs = arcs :+ Arc(s"${source}_valid", transId, 1)
      arcs = arcs :+ Arc(s"${dest}_available", transId, 1)
      arcs = arcs :+ Arc(transId, s"${source}_locked", 1)
      arcs = arcs :+ Arc(transId, s"${dest}_locked", 1)
      arcs = arcs :+ Arc(transId, s"${source}_available", 1)
      arcs = arcs :+ Arc(transId, s"${dest}_available", 1)
    }
    
    val initialMarking = Marking(initialMarkingMap)
    PetriNet(places, transitions, arcs, initialMarking)
  }
}
