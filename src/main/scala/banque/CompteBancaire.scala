package banque

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Compte {

  // ============ ÉTAT INTERNE DU COMPTE ============
  private case class CompteState(
    accountId: String,
    solde: Double,
    historique: List[Enregistrement] = List(),
    estActif: Boolean = true
  )

  // ============ BEHAVIOR DE L'ACTEUR ============
  def apply(accountId: String, soldeInitial: Double): Behavior[CommandeBancaire] =
    Behaviors.setup { context =>
      val initialState = CompteState(accountId, soldeInitial)
      context.log.info(s"Compte $accountId créé avec solde $soldeInitial")
      traiterCommandes(initialState)
    }

  private def traiterCommandes(state: CompteState): Behavior[CommandeBancaire] =
    Behaviors.receive { (context, command) =>
      command match {
        // ========== CONSULTER SOLDE ==========
        case ConsulterSolde(accountId, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouvé", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte fermé", System.currentTimeMillis())
            Behaviors.same
          } else {
            replyTo ! SoldeActuel(state.accountId, state.solde, System.currentTimeMillis())
            Behaviors.same
          }

        // ========== CONSULTER HISTORIQUE ==========
        case ConsulterHistorique(accountId, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouvé", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte fermé", System.currentTimeMillis())
            Behaviors.same
          } else {
            replyTo ! HistoriqueCompte(state.accountId, state.historique.reverse)
            Behaviors.same
          }

        // ========== DÉPÔT ==========
        case Deposer(accountId, montant, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouvé", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte fermé", System.currentTimeMillis())
            Behaviors.same
          } else if (montant <= 0) {
            replyTo ! OperationEchouee("Montant doit être positif", System.currentTimeMillis())
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
              s"Dépôt de $montant"
            )
            val newState = state.copy(
              solde = nouveauSolde,
              historique = state.historique :+ enregistrement
            )
            context.log.info(s"Dépôt de $montant sur ${state.accountId}, nouveau solde: $nouveauSolde")
            replyTo ! OperationReussie(txId, nouveauSolde, timestamp)
            traiterCommandes(newState)
          }

        // ========== RETRAIT ==========
        case Retirer(accountId, montant, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouvé", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte fermé", System.currentTimeMillis())
            Behaviors.same
          } else if (montant <= 0) {
            replyTo ! OperationEchouee("Montant doit être positif", System.currentTimeMillis())
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
            replyTo ! OperationEchouee(s"Compte source $accountIdSource non trouvé", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte fermé", System.currentTimeMillis())
            Behaviors.same
          } else if (montantSource <= 0) {
            replyTo ! OperationEchouee("Montant doit être positif", System.currentTimeMillis())
            Behaviors.same
          } else if (state.solde < montantSource) {
            replyTo ! OperationEchouee(s"Solde insuffisant (${state.solde})", System.currentTimeMillis())
            Behaviors.same
          } else {
            val txId = TransactionId()
            val timestamp = System.currentTimeMillis()
            val nouveauSolde = state.solde - montantSource

            // Envoyer au compte destinataire
            destinataire ! ReceptionVirement(accountIdSource, montantSource, txId, replyTo)

            // Enregistrer l'envoi localement
            val enregistrement = Enregistrement(
              txId,
              VIREMENT_ENVOI,
              montantSource,
              nouveauSolde,
              timestamp,
              s"Virement vers $accountIdDestination: $montantSource"
            )
            val newState = state.copy(
              solde = nouveauSolde,
              historique = state.historique :+ enregistrement
            )
            context.log.info(s"Virement de $montantSource de ${state.accountId} vers $accountIdDestination")
            traiterCommandes(newState)
          }

        // ========== RÉCEPTION VIREMENT ==========
        case ReceptionVirement(accountIdSource, montant, txId, replyTo) =>
          if (!state.estActif) {
            replyTo ! OperationEchouee("Compte destinataire fermé", System.currentTimeMillis())
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
              s"Virement reçu de $accountIdSource: $montant"
            )
            val newState = state.copy(
              solde = nouveauSolde,
              historique = state.historique :+ enregistrement
            )
            context.log.info(s"Réception virement de $montant sur ${state.accountId}, nouveau solde: $nouveauSolde")
            replyTo ! OperationReussie(txId, nouveauSolde, timestamp)
            traiterCommandes(newState)
          }

        // ========== CRÉER COMPTE ==========
        case CreerCompte(accountId, soldeInitial, replyTo) =>
          if (accountId == state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId déjà existant", System.currentTimeMillis())
            Behaviors.same
          } else if (soldeInitial < 0) {
            replyTo ! OperationEchouee("Solde initial ne peut pas être négatif", System.currentTimeMillis())
            Behaviors.same
          } else {
            val timestamp = System.currentTimeMillis()
            context.log.info(s"Création du compte $accountId avec solde $soldeInitial")
            replyTo ! CompteCreé(accountId, soldeInitial, timestamp)
            Behaviors.same
          }

        // ========== FERMER COMPTE ==========
        case FermerCompte(accountId, replyTo) =>
          if (accountId != state.accountId) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouvé", System.currentTimeMillis())
            Behaviors.same
          } else if (!state.estActif) {
            replyTo ! OperationEchouee("Compte déjà fermé", System.currentTimeMillis())
            Behaviors.same
          } else {
            val timestamp = System.currentTimeMillis()
            val closedState = state.copy(estActif = false)
            context.log.info(s"Compte ${state.accountId} fermé avec solde restitué: ${state.solde}")
            replyTo ! CompteFermé(state.accountId, state.solde, timestamp)
            traiterCommandes(closedState)
          }
      }
    }
}
