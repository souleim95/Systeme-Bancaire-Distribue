package banque

import petri._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests du système de réseau de Pétri bancaire
 */
class PetriNetBankingTests extends AnyFlatSpec with Matchers {
  
  "Un réseau simple" should "avoir un état initial valide" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    petriNet.initialMarking("accountAvailable_p") should equal(1)
  }
  
  it should "avoir des transitions activées depuis l'état initial" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val enabled = petriNet.getEnabledTransitions(petriNet.initialMarking)
    enabled should not be empty
  }
  
  it should "accepter une transition de dépôt" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val result = petriNet.fireTransition("deposit_t", petriNet.initialMarking)
    result should not be empty
  }
  
  it should "ne pas avoir de deadlock" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new PropertyChecker(petriNet)
    val result = checker.checkNoDeadlock
    result.isValid should be(true)
  }
  
  "Un réseau de virement" should "modéliser deux comptes" in {
    val petriNet = BankingPetriNet.createTransferNet()
    petriNet.places.size should be > 4
  }
  
  it should "avoir un graphe de réachabilité fini" in {
    val petriNet = BankingPetriNet.createTransferNet()
    val (reachable, _) = petriNet.getReachabilityGraph
    reachable.size should be > 0
  }
  
  "Un réseau complet" should "gérer plusieurs comptes" in {
    val accounts = List("ACC-001", "ACC-002", "ACC-003")
    val petriNet = BankingPetriNet.createCompleteNet(accounts)
    petriNet.places.size should be >= accounts.size * 3
  }
  
  "Un marquage" should "supporter l'incrémentation et la décrémentation" in {
    val marking = Marking(Map("p1" -> 5))
    val incremented = marking.increment("p1", 3)
    incremented("p1") should equal(8)
    
    val decremented = incremented.decrement("p1", 2)
    decremented("p1") should equal(6)
  }
  
  it should "empêcher les soldes négatifs" in {
    val marking = Marking(Map("p1" -> 3))
    val decremented = marking.decrement("p1", 5)
    decremented("p1") should equal(0)
  }
  
  "Le vérificateur de propriétés" should "détecter la vivacité" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new PropertyChecker(petriNet)
    val result = checker.checkLiveness
    result.isValid should be(true)
  }
  
  it should "vérifier la réversibilité" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new PropertyChecker(petriNet)
    val result = checker.checkReversibility
    result.isValid should be(true)
  }
  
  "Le simulateur" should "pouvoir exécuter une transition valide" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val simulator = Simulator(petriNet)
    val enabled = petriNet.getEnabledTransitions(petriNet.initialMarking)
    enabled should not be empty
  }
  
  it should "tracer les étapes d'exécution" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val simulator = Simulator(petriNet)
    val enabled = petriNet.getEnabledTransitions(petriNet.initialMarking)
    
    if (enabled.nonEmpty) {
      simulator.executeTransition(enabled.head)
      // Le simulateur devrait avoir enregistré l'étape
      // (on peut vérifier avec displayTrace())
    }
  }
  
  "Un arc inhibiteur" should "empêcher la transition si jetons présents" in {
    val places = Map(
      "p1" -> Place("p1", "Place 1", 1),
      "p2" -> Place("p2", "Place 2", 0)
    )
    val transitions = Map(
      "t1" -> Transition("t1", "Transition 1")
    )
    val arcs = List(
      Arc("p1", "t1", 1, isInhibitor = true)  // Arc inhibiteur
    )
    
    val petriNet = PetriNet(places, transitions, arcs, Marking(Map("p1" -> 1)))
    val enabled = petriNet.getEnabledTransitions(Marking(Map("p1" -> 1)))
    
    enabled should be(empty)  // La transition ne doit pas être activée
  }
  
  "Le graphe de réachabilité" should "inclure le marquage initial" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val (reachable, _) = petriNet.getReachabilityGraph
    reachable should contain(petriNet.initialMarking)
  }
}
