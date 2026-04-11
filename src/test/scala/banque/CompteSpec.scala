package banque

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers._

class CompteSpec extends AnyWordSpecLike with BeforeAndAfterAll {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "Un Compte Bancaire" should {
    "accepter un dépôt et augmenter le solde" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC001", 100.0))

      compte ! Deposer("ACC001", 50.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationReussie]

      response.nouveauSolde shouldEqual 150.0
    }

    "refuser un depot avec montant negatif" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC002", 100.0))

      compte ! Deposer("ACC002", -50.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Montant doit etre positif"
    }

    "accepter un retrait si le solde est suffisant" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC003", 100.0))

      compte ! Retirer("ACC003", 30.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationReussie]

      response.nouveauSolde shouldEqual 70.0
    }

    "refuser un retrait si solde insuffisant" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC004", 50.0))

      compte ! Retirer("ACC004", 100.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Solde insuffisant (50.0)"
    }

    "retourner le solde actuel" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC005", 250.0))

      compte ! ConsulterSolde("ACC005", replyProbe.ref)
      val response = replyProbe.expectMessageType[SoldeActuel]

      response.solde shouldEqual 250.0
    }

    "retourner l'historique des transactions" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC006", 100.0))

      // Faire quelques opérations
      compte ! Deposer("ACC006", 50.0, replyProbe.ref)
      replyProbe.expectMessageType[OperationReussie]

      compte ! Retirer("ACC006", 30.0, replyProbe.ref)
      replyProbe.expectMessageType[OperationReussie]

      // Consulter l'historique
      compte ! ConsulterHistorique("ACC006", replyProbe.ref)
      val response = replyProbe.expectMessageType[HistoriqueCompte]

      response.transactions should have length 2
      response.transactions.head.type_ shouldEqual RETRAIT
      response.transactions(1).type_ shouldEqual DEPOT
    }

    "permettre un virement entre comptes" in {
      val replyProbe1 = testKit.createTestProbe[ReponseBancaire]()
      val replyProbe2 = testKit.createTestProbe[ReponseBancaire]()

      val compte1 = testKit.spawn(Compte("ACC007", 200.0))
      val compte2 = testKit.spawn(Compte("ACC008", 100.0))

      // Envoyer un virement
      compte1 ! Virement("ACC007", 50.0, "ACC008", compte2, replyProbe1.ref)
      replyProbe1.expectMessageType[OperationReussie]

      // Vérifier que compte1 a perdu 50
      compte1 ! ConsulterSolde("ACC007", replyProbe1.ref)
      val solde1 = replyProbe1.expectMessageType[SoldeActuel]
      solde1.solde shouldEqual 150.0

      // Recevoir la réception du virement (le message ReceptionVirement est envoyé automatiquement)
      // Vérifier que compte2 a reçu 50
      compte2 ! ConsulterSolde("ACC008", replyProbe2.ref)
      val solde2 = replyProbe2.expectMessageType[SoldeActuel]
      solde2.solde shouldEqual 150.0
    }

    "refuser un virement si solde insuffisant" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte2 = testKit.spawn(Compte("ACC009", 100.0))
      val compte1 = testKit.spawn(Compte("ACC010", 30.0))

      compte1 ! Virement("ACC010", 50.0, "ACC009", compte2, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Solde insuffisant (30.0)"
    }

    "permettre de fermer un compte" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC011", 500.0))

      compte ! FermerCompte("ACC011", replyProbe.ref)
      val response = replyProbe.expectMessageType[CompteFermé]

      response.soldeRestitue shouldEqual 500.0
    }

    "refuser les operations sur un compte ferme" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC012", 200.0))

      // Fermer le compte
      compte ! FermerCompte("ACC012", replyProbe.ref)
      replyProbe.expectMessageType[CompteFermé]

      // Essayer un depot
      compte ! Deposer("ACC012", 50.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Compte ferme"
    }

    "refuser les operations sur un compte inexistant" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val compte = testKit.spawn(Compte("ACC013", 100.0))

      compte ! Deposer("ACC_INEXISTANT", 50.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Compte ACC_INEXISTANT non trouve"
    }
  }
}
