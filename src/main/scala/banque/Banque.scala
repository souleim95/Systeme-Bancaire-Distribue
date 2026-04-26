package banque

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef

object Banque {

  // ============ COMMANDES BANQUE ============
  sealed trait CommandeBanque
  case class CreerCompteReq(
    accountId: String,
    soldeInitial: Double,
    replyTo: ActorRef[ReponseBancaire]
  ) extends CommandeBanque

  case class OperationCompteBanque(
    command: CommandeBancaire
  ) extends CommandeBanque

  case class ListerComptesReq(
    replyTo: ActorRef[ListeComptes]
  ) extends CommandeBanque

  case class ConsulterSoldeGlobalReq(
    replyTo: ActorRef[SoldeGlobal]
  ) extends CommandeBanque

  case class SupprimerCompteReq(
    accountId: String,
    replyTo: ActorRef[ReponseBancaire]
  ) extends CommandeBanque

  // ============ MESSAGES INTERNES ============
  private case class ForwardResponse(
    accountId: String,
    originalReplyTo: ActorRef[ReponseBancaire],
    response: ReponseBancaire
  ) extends CommandeBanque

  private case class ForwardTransferResponse(
    sourceAccountId: String,
    destinationAccountId: String,
    montant: Double,
    originalReplyTo: ActorRef[ReponseBancaire],
    response: ReponseBancaire
  ) extends CommandeBanque

  // ============ REPONSES ============
  case class ListeComptes(comptes: Map[String, Double])

  case class SoldeGlobal(total: Double, nombreComptes: Int)

  // ============ ETAT INTERNE ============
  private case class BanqueState(
    comptes: Map[String, ActorRef[CommandeBancaire]] = Map(),
    soldes: Map[String, Double] = Map()
  )

  // ============ BEHAVIOR ============
  def apply(): Behavior[CommandeBanque] =
    Behaviors.setup { context =>
      context.log.info("Banque creee")
      traiterCommandes(BanqueState())
    }

  private def traiterCommandes(state: BanqueState): Behavior[CommandeBanque] =
    Behaviors.receive { (context, command) =>
      command match {
        // ========== CREER COMPTE ==========
        case CreerCompteReq(accountId, soldeInitial, replyTo) =>
          if (state.comptes.contains(accountId)) {
            replyTo ! OperationEchouee(s"Compte $accountId existe deja", System.currentTimeMillis())
            Behaviors.same
          } else if (soldeInitial < 0) {
            replyTo ! OperationEchouee("Solde initial ne peut pas etre negatif", System.currentTimeMillis())
            Behaviors.same
          } else {
            val compteActor = context.spawn(Compte(accountId, soldeInitial), s"compte-$accountId")
            val newState = state.copy(
              comptes = state.comptes + (accountId -> compteActor),
              soldes = state.soldes + (accountId -> soldeInitial)
            )
            context.log.info(s"Compte $accountId cree par la banque avec solde $soldeInitial")
            replyTo ! CompteCreé(accountId, soldeInitial, System.currentTimeMillis())
            traiterCommandes(newState)
          }

        // ========== OPERATION COMPTE ==========
        case OperationCompteBanque(cmd) =>
          cmd match {
            case Deposer(accountId, montant, replyTo) =>
              if (!state.comptes.contains(accountId)) {
                replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
                Behaviors.same
              } else {
                val replyAdapter = forwardResponseActor(context, accountId, replyTo)
                state.comptes(accountId) ! Deposer(accountId, montant, replyAdapter)
                Behaviors.same
              }

            case Retirer(accountId, montant, replyTo) =>
              if (!state.comptes.contains(accountId)) {
                replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
                Behaviors.same
              } else {
                val replyAdapter = forwardResponseActor(context, accountId, replyTo)
                state.comptes(accountId) ! Retirer(accountId, montant, replyAdapter)
                Behaviors.same
              }

            case Virement(accountIdSource, montantSource, accountIdDestination, _, replyTo) =>
              if (!state.comptes.contains(accountIdSource)) {
                replyTo ! OperationEchouee(s"Compte source $accountIdSource non trouve", System.currentTimeMillis())
                Behaviors.same
              } else if (!state.comptes.contains(accountIdDestination)) {
                replyTo ! OperationEchouee(s"Compte destination $accountIdDestination non trouve", System.currentTimeMillis())
                Behaviors.same
              } else {
                val compteDestinaire = state.comptes(accountIdDestination)
                val replyAdapter = forwardTransferResponseActor(
                  context,
                  accountIdSource,
                  accountIdDestination,
                  montantSource,
                  replyTo
                )
                state.comptes(accountIdSource) ! Virement(
                  accountIdSource,
                  montantSource,
                  accountIdDestination,
                  compteDestinaire,
                  replyAdapter
                )
                Behaviors.same
              }

            case ConsulterSolde(accountId, replyTo) =>
              if (!state.comptes.contains(accountId)) {
                replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
                Behaviors.same
              } else {
                val replyAdapter = forwardResponseActor(context, accountId, replyTo)
                state.comptes(accountId) ! ConsulterSolde(accountId, replyAdapter)
                Behaviors.same
              }

            case ConsulterHistorique(accountId, replyTo) =>
              if (!state.comptes.contains(accountId)) {
                replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
                Behaviors.same
              } else {
                val replyAdapter = forwardResponseActor(context, accountId, replyTo)
                state.comptes(accountId) ! ConsulterHistorique(accountId, replyAdapter)
                Behaviors.same
              }

            case FermerCompte(accountId, replyTo) =>
              if (!state.comptes.contains(accountId)) {
                replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
                Behaviors.same
              } else {
                val replyAdapter = forwardResponseActor(context, accountId, replyTo)
                state.comptes(accountId) ! FermerCompte(accountId, replyAdapter)
                Behaviors.same
              }

            case _ =>
              context.log.warn("Commande non supportee")
              Behaviors.same
          }

        // ========== LISTER COMPTES ==========
        case ListerComptesReq(replyTo) =>
          replyTo ! ListeComptes(state.soldes)
          Behaviors.same

        // ========== CONSULTER SOLDE GLOBAL ==========
        case ConsulterSoldeGlobalReq(replyTo) =>
          val total = state.soldes.values.sum
          replyTo ! SoldeGlobal(total, state.comptes.size)
          Behaviors.same

        // ========== RÉPONSE COMPTE FORWARDÉE ==========
        case ForwardResponse(accountId, originalReplyTo, response) =>
          originalReplyTo ! response
          val newState = updateStateFromResponse(state, accountId, response, context)
          traiterCommandes(newState)

        // ========== RÉPONSE VIREMENT FORWARDÉE ==========
        case ForwardTransferResponse(sourceAccountId, destinationAccountId, montant, originalReplyTo, response) =>
          originalReplyTo ! response
          val newState = response match {
            case _: OperationReussie =>
              val soldeSource = state.soldes.getOrElse(sourceAccountId, 0.0)
              val soldeDestination = state.soldes.getOrElse(destinationAccountId, 0.0)
              state.copy(
                soldes = state.soldes
                  .updated(sourceAccountId, soldeSource - montant)
                  .updated(destinationAccountId, soldeDestination + montant)
              )
            case _ => state
          }
          traiterCommandes(newState)

        // ========== SUPPRIMER COMPTE ==========
        case SupprimerCompteReq(accountId, replyTo) =>
          if (!state.comptes.contains(accountId)) {
            replyTo ! OperationEchouee(s"Compte $accountId non trouve", System.currentTimeMillis())
            Behaviors.same
          } else {
            context.stop(state.comptes(accountId))
            val newState = state.copy(
              comptes = state.comptes - accountId,
              soldes = state.soldes - accountId
            )
            context.log.info(s"Compte $accountId supprime de la banque")
            replyTo ! OperationReussie(TransactionId(), 0, System.currentTimeMillis())
            traiterCommandes(newState)
          }
      }
    }

  private def forwardResponseActor(
    context: akka.actor.typed.scaladsl.ActorContext[CommandeBanque],
    accountId: String,
    originalReplyTo: ActorRef[ReponseBancaire]
  ): ActorRef[ReponseBancaire] =
    context.spawnAnonymous(
      Behaviors.receiveMessage[ReponseBancaire] { response =>
        context.self ! ForwardResponse(accountId, originalReplyTo, response)
        Behaviors.stopped
      }
    )

  private def forwardTransferResponseActor(
    context: akka.actor.typed.scaladsl.ActorContext[CommandeBanque],
    sourceAccountId: String,
    destinationAccountId: String,
    montant: Double,
    originalReplyTo: ActorRef[ReponseBancaire]
  ): ActorRef[ReponseBancaire] =
    context.spawnAnonymous(
      Behaviors.receiveMessage[ReponseBancaire] { response =>
        context.self ! ForwardTransferResponse(sourceAccountId, destinationAccountId, montant, originalReplyTo, response)
        Behaviors.stopped
      }
    )

  private def updateStateFromResponse(
    state: BanqueState,
    requestedAccountId: String,
    response: ReponseBancaire,
    context: akka.actor.typed.scaladsl.ActorContext[CommandeBanque]
  ): BanqueState = response match {
    case OperationReussie(_, nouveauSolde, _) =>
      state.copy(soldes = state.soldes.updated(requestedAccountId, nouveauSolde))
    case SoldeActuel(accountId, solde, _) =>
      state.copy(soldes = state.soldes.updated(accountId, solde))
    case CompteFermé(accountId, _, _) =>
      state.comptes.get(accountId).foreach(context.stop)
      state.copy(
        comptes = state.comptes - accountId,
        soldes = state.soldes - accountId
      )
    case _ =>
      state
  }
}
