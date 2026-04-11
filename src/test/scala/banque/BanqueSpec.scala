package banque

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers._

class BanqueSpec extends AnyWordSpecLike with BeforeAndAfterAll {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "Une Banque" should {
    "creer un compte avec solde positif" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B01", 1000.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[CompteCreé]

      response.accountId shouldEqual "ACC-B01"
      response.solde shouldEqual 1000.0
    }

    "refuser un compte avec solde negatif" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B02", -100.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Solde initial ne peut pas etre negatif"
    }

    "refuser creer un compte qui existe deja" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B03", 500.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      banque ! Banque.CreerCompteReq("ACC-B03", 500.0, replyProbe.ref)
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Compte ACC-B03 existe deja"
    }

    "faire un depot sur un compte existant" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B04", 1000.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      banque ! Banque.OperationCompteBanque(
        Deposer("ACC-B04", 200.0, replyProbe.ref)
      )
      val response = replyProbe.expectMessageType[OperationReussie]

      response.nouveauSolde shouldEqual 1200.0
    }

    "refuser operation sur compte inexistant" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.OperationCompteBanque(
        Deposer("ACC-INEXISTANT", 100.0, replyProbe.ref)
      )
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Compte ACC-INEXISTANT non trouve"
    }

    "faire un virement entre deux comptes" in {
      val replyProbe1 = testKit.createTestProbe[ReponseBancaire]()
      val replyProbe2 = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      // Creer deux comptes
      banque ! Banque.CreerCompteReq("ACC-B05", 1000.0, replyProbe1.ref)
      replyProbe1.expectMessageType[CompteCreé]

      banque ! Banque.CreerCompteReq("ACC-B06", 500.0, replyProbe1.ref)
      replyProbe1.expectMessageType[CompteCreé]

      // Virement
      banque ! Banque.OperationCompteBanque(
        Virement("ACC-B05", 300.0, "ACC-B06", testKit.createTestProbe().ref, replyProbe1.ref)
      )
      replyProbe1.expectMessageType[OperationReussie]

      // Verifier soldes
      banque ! Banque.OperationCompteBanque(
        ConsulterSolde("ACC-B05", replyProbe1.ref)
      )
      val solde1 = replyProbe1.expectMessageType[SoldeActuel]
      solde1.solde shouldEqual 700.0

      banque ! Banque.OperationCompteBanque(
        ConsulterSolde("ACC-B06", replyProbe2.ref)
      )
      val solde2 = replyProbe2.expectMessageType[SoldeActuel]
      solde2.solde shouldEqual 800.0
    }

    "lister les comptes" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B07", 1000.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      banque ! Banque.CreerCompteReq("ACC-B08", 500.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      val listeProbe = testKit.createTestProbe[Banque.ListeComptes]()
      banque ! Banque.ListerComptesReq(listeProbe.ref)
      val response = listeProbe.expectMessageType[Banque.ListeComptes]

      response.comptes should have size 2
      response.comptes("ACC-B07") shouldEqual 1000.0
      response.comptes("ACC-B08") shouldEqual 500.0
    }

    "consulter solde global" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B09", 1000.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      banque ! Banque.CreerCompteReq("ACC-B10", 500.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      banque ! Banque.CreerCompteReq("ACC-B11", 750.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      val soldeProbe = testKit.createTestProbe[Banque.SoldeGlobal]()
      banque ! Banque.ConsulterSoldeGlobalReq(soldeProbe.ref)
      val response = soldeProbe.expectMessageType[Banque.SoldeGlobal]

      response.total shouldEqual 2250.0
      response.nombreComptes shouldEqual 3
    }

    "supprimer un compte" in {
      val replyProbe = testKit.createTestProbe[ReponseBancaire]()
      val banque = testKit.spawn(Banque())

      banque ! Banque.CreerCompteReq("ACC-B12", 1000.0, replyProbe.ref)
      replyProbe.expectMessageType[CompteCreé]

      banque ! Banque.SupprimerCompteReq("ACC-B12", replyProbe.ref)
      replyProbe.expectMessageType[OperationReussie]

      // Verifier que le compte n'existe plus
      banque ! Banque.OperationCompteBanque(
        ConsulterSolde("ACC-B12", replyProbe.ref)
      )
      val response = replyProbe.expectMessageType[OperationEchouee]

      response.raison shouldEqual "Compte ACC-B12 non trouve"
    }
  }
}
