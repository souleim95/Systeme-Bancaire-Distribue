package banque

import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors

object Compte {

  // ============ ETAT INTERNE DU COMPTE ============
  private case class PendingTransfer(
    destinationId: String,
    montant: Double,
    replyTo: ActorRef[ReponseBancaire]
  )

  private case class CompteState(
    accountId: String,
    solde: Double,
    historique: List[Enregistrement] = List(),
    estActif: Boolean = true,
    pendingTransfers: Map[TransactionId, PendingTransfer] = Map.empty
  )

  // ============ BEHAVIOR DE L'ACTEUR ============
  def apply(accountId: String, soldeInitial: Double): Behavior[CommandeBancaire] =
    Behaviors.setup { context =>
      val initialState = CompteState(accountId, soldeInitial)
      context.log.info(s"Compte $accountId cree avec solde $soldeInitial")
      traiterCommandes(initialState)
    }

  private def traiterCommandes(state: CompteState): Behavior[CommandeBancaire] =
    Behaviors.receive { (context, command) =>
      command match {
        // ========== CONSULTER SOLDE ==========
        case ConsulterSolde(accountId, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte ferme", System.currentTimeMillis())
            Behaviors.same
          } else {
            replyTo ! SoldeActuel(state.accountId, state.solde, System.currentTimeMillis())
            Behaviors.same
          }

        // ========== CONSULTER HISTORIQUE ==========
        case ConsulterHistorique(accountId, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte ferme", System.currentTimeMillis())
            Behaviors.same
          } else {
            replyTo ! HistoriqueCompte(state.accountId, state.historique.reverse)
            Behaviors.same
          }

        // ========== DEPOT ==========
        case Deposer(accountId, montant, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte ferme", System.currentTimeMillis())
            Behaviors.same
          } else if (montant <= 0) {
            replyTo ! OperationEchouee("Montant doit etre positif", System.currentTimeMillis())
            Behaviors.same
          } else {
            val nouveauSolde = state.solde + montant
            val txId = TransactionId()
            val timestamp = System.currentTimeMillis()
            val enregistrement = Enregistrement(
              txId,
              DEPOT,
              montant,
              nouveauSolde,
              timestamp,
              s"Depot de $montant"
            )
            val newState = state.copy(
              solde = nouveauSolde,
              historique = state.historique :+ enregistrement
            )
            context.log.info(s"Depot de $montant sur ${state.accountId}, nouveau solde: $nouveauSolde")
            replyTo ! OperationReussie(txId, nouveauSolde, timestamp)
            traiterCommandes(newState)
          }

        // ========== RETRAIT ==========
        case Retirer(accountId, montant, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte ferme", System.currentTimeMillis())
            Behaviors.same
          } else if (montant <= 0) {
            replyTo ! OperationEchouee("Montant doit etre positif", System.currentTimeMillis())
            Behaviors.same
          } else if (state.solde < montant) {
            replyTo ! OperationEchouee(s"Solde insuffisant (${state.solde})", System.currentTimeMillis())
            Behaviors.same
          } else {
            val nouveauSolde = state.solde - montant
            val txId = TransactionId()
            val timestamp = System.currentTimeMillis()
            val enregistrement = Enregistrement(
              txId,
              RETRAIT,
              montant,
              nouveauSolde,
              timestamp,
              s"Retrait de $montant"
            )
            val newState = state.copy(
              solde = nouveauSolde,
              historique = state.historique :+ enregistrement
            )
            context.log.info(s"Retrait de $montant sur ${state.accountId}, nouveau solde: $nouveauSolde")
            replyTo ! OperationReussie(txId, nouveauSolde, timestamp)
            traiterCommandes(newState)
          }

        // ========== VIREMENT (ENVOI) ==========
        case Virement(accountIdSource, montantSource, accountIdDestination, destinataire, replyTo) =>
          if (accountIdSource != state.accountId) {
            replyTo ! OperationEchouee(s"Compte source $accountIdSource non trouve", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte ferme", System.currentTimeMillis())
            Behaviors.same
          } else if (montantSource <= 0) {
            replyTo ! OperationEchouee("Montant doit etre positif", System.currentTimeMillis())
            Behaviors.same
          } else if (state.solde < montantSource) {
            replyTo ! OperationEchouee(s"Solde insuffisant (${state.solde})", System.currentTimeMillis())
            Behaviors.same
          } else {
            val txId = TransactionId()
            val nouveauSolde = state.solde - montantSource

            val confirmationReceiver = context.spawnAnonymous(
              Behaviors.receiveMessage[ReponseBancaire] { response =>
                context.self ! ConfirmationVirement(
                  accountIdSource,
                  accountIdDestination,
                  montantSource,
                  txId,
                  replyTo,
                  response
                )
                Behaviors.stopped
              }
            )

            // Le montant est reserve immediatement pour empecher un second debit concurrent.
            destinataire ! ReceptionVirement(accountIdSource, montantSource, txId, confirmationReceiver)

            val newState = state.copy(
              solde = nouveauSolde,
              pendingTransfers = state.pendingTransfers + (
                txId -> PendingTransfer(accountIdDestination, montantSource, replyTo)
              )
            )
            context.log.info(s"Virement de $montantSource de ${state.accountId} vers $accountIdDestination en attente de confirmation")
            traiterCommandes(newState)
          }

        // ========== CONFIRMATION VIREMENT ==========
        case ConfirmationVirement(accountIdSource, _, _, txId, originalReplyTo, destinationResponse) =>
          if (accountIdSource != state.accountId) {
            originalReplyTo ! OperationEchouee(s"Compte source $accountIdSource non trouve", System.currentTimeMillis())
            Behaviors.same
          } else {
            state.pendingTransfers.get(txId) match {
              case None =>
                originalReplyTo ! OperationEchouee("Virement inconnu ou deja traite", System.currentTimeMillis())
                Behaviors.same

              case Some(pending) =>
                val timestamp = System.currentTimeMillis()
                destinationResponse match {
                  case _: OperationReussie =>
                    val enregistrement = Enregistrement(
                      txId,
                      VIREMENT_ENVOI,
                      pending.montant,
                      state.solde,
                      timestamp,
                      s"Virement vers ${pending.destinationId}: ${pending.montant}"
                    )
                    val newState = state.copy(
                      historique = state.historique :+ enregistrement,
                      pendingTransfers = state.pendingTransfers - txId
                    )
                    context.log.info(s"Virement de ${pending.montant} de ${state.accountId} vers ${pending.destinationId} confirme")
                    pending.replyTo ! OperationReussie(txId, state.solde, timestamp)
                    traiterCommandes(newState)

                  case OperationEchouee(raison, _) =>
                    val refundedState = state.copy(
                      solde = state.solde + pending.montant,
                      pendingTransfers = state.pendingTransfers - txId
                    )
                    context.log.warn(s"Virement de ${pending.montant} de ${state.accountId} vers ${pending.destinationId} annule: $raison")
                    pending.replyTo ! OperationEchouee(s"Virement annule: $raison", timestamp)
                    traiterCommandes(refundedState)

                  case _ =>
                    val refundedState = state.copy(
                      solde = state.solde + pending.montant,
                      pendingTransfers = state.pendingTransfers - txId
                    )
                    context.log.warn(s"Virement de ${pending.montant} de ${state.accountId} vers ${pending.destinationId} annule: reponse inattendue")
                    pending.replyTo ! OperationEchouee("Virement annule: reponse destinataire inattendue", timestamp)
                    traiterCommandes(refundedState)
                }
            }
          }

        // ========== RECEPTION VIREMENT ==========
        case ReceptionVirement(accountIdSource, montant, txId, replyTo) =>
          if (!state.estActif) {
            replyTo ! OperationEchouee("Compte destinataire ferme", System.currentTimeMillis())
            Behaviors.same
          } else {
            val nouveauSolde = state.solde + montant
            val timestamp = System.currentTimeMillis()
            val enregistrement = Enregistrement(
              txId,
              VIREMENT_RECEPTION,
              montant,
              nouveauSolde,
              timestamp,
              s"Virement recu de $accountIdSource: $montant"
            )
            val newState = state.copy(
              solde = nouveauSolde,
              historique = state.historique :+ enregistrement
            )
            context.log.info(s"Reception virement de $montant sur ${state.accountId}, nouveau solde: $nouveauSolde")
            replyTo ! OperationReussie(txId, nouveauSolde, timestamp)
            traiterCommandes(newState)
          }

        // ========== CREER COMPTE ==========
        case CreerCompte(accountId, soldeInitial, replyTo) =>
          if (accountId == state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId deja existant", System.currentTimeMillis())
            Behaviors.same
          } else if (soldeInitial < 0) {
            replyTo ! OperationEchouee("Solde initial ne peut pas etre negatif", System.currentTimeMillis())
            Behaviors.same
          } else {
            val timestamp = System.currentTimeMillis()
            context.log.info(s"Creation du compte $accountId avec solde $soldeInitial")
            replyTo ! CompteCreé(accountId, soldeInitial, timestamp)
            Behaviors.same
          }

        // ========== FERMER COMPTE ==========
        case FermerCompte(accountId, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte deja ferme", System.currentTimeMillis())
            Behaviors.same
          } else if (state.pendingTransfers.nonEmpty) {
            replyTo ! OperationEchouee("Operation en cours, fermeture impossible", System.currentTimeMillis())
            Behaviors.same
          } else {
            val timestamp = System.currentTimeMillis()
            val closedState = state.copy(estActif = false)
            context.log.info(s"Compte ${state.accountId} ferme avec solde restitue: ${state.solde}")
            replyTo ! CompteFermé(state.accountId, state.solde, timestamp)
            traiterCommandes(closedState)
          }
      }
    }
}
