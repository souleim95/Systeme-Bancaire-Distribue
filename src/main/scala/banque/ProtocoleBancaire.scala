package banque

import akka.actor.typed.ActorRef
import scala.util.Random

// ============ MODÈLES UTILITAIRES ============

case class TransactionId(value: String = f"TXN_${System.currentTimeMillis()}_${Random.nextInt(10000)}")

sealed trait TypeTransaction
case object DEPOT extends TypeTransaction
case object RETRAIT extends TypeTransaction
case object VIREMENT_ENVOI extends TypeTransaction
case object VIREMENT_RECEPTION extends TypeTransaction

case class Enregistrement(
  id: TransactionId,
  type_ : TypeTransaction,
  montant: Double,
  soldeApres: Double,
  timestamp: Long,
  description: String
)

// ============ LES COMMANDES ============

sealed trait CommandeBancaire

// Opérations basiques
case class Deposer(
  accountId: String,
  montant: Double,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

case class Retirer(
  accountId: String,
  montant: Double,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

case class Virement(
  accountIdSource: String,
  montantSource: Double,
  accountIdDestination: String,
  destinataire: ActorRef[CommandeBancaire],
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

// Consultation
case class ConsulterSolde(
  accountId: String,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

case class ConsulterHistorique(
  accountId: String,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

// Gestion du compte
case class CreerCompte(
  accountId: String,
  soldeInitial: Double,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

case class FermerCompte(
  accountId: String,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

// Message interne pour la réception de virement
case class ReceptionVirement(
  accountIdSource: String,
  montant: Double,
  transactionId: TransactionId,
  replyTo: ActorRef[ReponseBancaire]
) extends CommandeBancaire

// Message interne: confirmation recue par le compte source apres reponse du destinataire
case class ConfirmationVirement(
  accountIdSource: String,
  accountIdDestination: String,
  montant: Double,
  transactionId: TransactionId,
  originalReplyTo: ActorRef[ReponseBancaire],
  destinationResponse: ReponseBancaire
) extends CommandeBancaire

// ============ LES RÉPONSES ============

sealed trait ReponseBancaire

case class OperationReussie(
  transactionId: TransactionId,
  nouveauSolde: Double,
  timestamp: Long
) extends ReponseBancaire

case class OperationEchouee(
  raison: String,
  timestamp: Long
) extends ReponseBancaire

case class SoldeActuel(
  accountId: String,
  solde: Double,
  timestamp: Long
) extends ReponseBancaire

case class HistoriqueCompte(
  accountId: String,
  transactions: List[Enregistrement]
) extends ReponseBancaire

case class VirementEnvoyeAvecConfirmation(
  transactionId: TransactionId,
  montant: Double,
  nouveauSolde: Double,
  confirmationDestination: Boolean,
  timestamp: Long
) extends ReponseBancaire

case class CompteCreé(
  accountId: String,
  solde: Double,
  timestamp: Long
) extends ReponseBancaire

case class CompteFermé(
  accountId: String,
  soldeRestitue: Double,
  timestamp: Long
) extends ReponseBancaire
