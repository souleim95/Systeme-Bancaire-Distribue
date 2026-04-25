package banque

import petri._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests du parseur et vérificateur LTL
 */
class LTLTests extends AnyFlatSpec with Matchers {
  
  "Un parser LTL" should "parser les atomes simples" in {
    val formula = LTLParser.parse("p")
    formula shouldBe Atom("p")
  }
  
  it should "parser les opérateurs NOT" in {
    val formula = LTLParser.parse("!p")
    formula shouldBe Not(Atom("p"))
  }
  
  it should "parser les opérateurs AND" in {
    val formula = LTLParser.parse("p & q")
    formula shouldBe And(Atom("p"), Atom("q"))
  }
  
  it should "parser les opérateurs OR" in {
    val formula = LTLParser.parse("p | q")
    formula shouldBe Or(Atom("p"), Atom("q"))
  }
  
  it should "parser l'opérateur X (next)" in {
    val formula = LTLParser.parse("X p")
    formula shouldBe Next(Atom("p"))
  }
  
  it should "parser l'opérateur F (finally)" in {
    val formula = LTLParser.parse("F p")
    formula shouldBe Finally(Atom("p"))
  }
  
  it should "parser l'opérateur G (globally)" in {
    val formula = LTLParser.parse("G p")
    formula shouldBe Globally(Atom("p"))
  }
  
  it should "parser l'opérateur U (until)" in {
    val formula = LTLParser.parse("p U q")
    formula shouldBe Until(Atom("p"), Atom("q"))
  }
  
  it should "parser les formules complexes avec parenthèses" in {
    val formula = LTLParser.parse("(p & q) | r")
    formula shouldBe Or(And(Atom("p"), Atom("q")), Atom("r"))
  }
  
  it should "parser les formules imbriquées" in {
    val formula = LTLParser.parse("G (p -> F q)")
    // Simplement vérifier que ça parse sans erreur
    formula should not be null
  }
  
  it should "rejeter les formules invalides" in {
    assertThrows[ParseException] {
      LTLParser.parse("p & & q")
    }
  }
  
  "Un vérificateur LTL" should "vérifier une formule simple sur un réseau" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new LTLModelChecker(petriNet)
    
    val result = checker.check("true")
    result.isValid should be(true)
  }
  
  it should "rejeter une formule fausse" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new LTLModelChecker(petriNet)
    
    val result = checker.check("false")
    result.isValid should be(false)
  }
  
  it should "vérifier les propriétés de places" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new LTLModelChecker(petriNet)
    
    // Le compte doit être disponible initialement
    val result = checker.check("has_accountAvailable_p")
    result.isValid should be(true)
  }
  
  it should "vérifier les formules composées" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new LTLModelChecker(petriNet)
    
    val result = checker.check("G true")
    result.isValid should be(true)
  }
  
  "Un modèle checker complet" should "générer un rapport" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new LTLModelChecker(petriNet)
    
    val formulas = List(
      "true",
      "false",
      "G true"
    )
    
    val results = checker.checkAll(formulas)
    results should have size 3
    results.count(_.isValid) should be >= 1
  }
  
  it should "afficher correctement les résultats" in {
    val petriNet = BankingPetriNet.createSingleAccountNet()
    val checker = new LTLModelChecker(petriNet)
    
    val result = LTLVerificationResult(
      formula = "G (p | q)",
      isValid = true,
      message = "Formula is satisfied"
    )
    
    result.toString should include("VALID")
    result.toString should include("G (p | q)")
  }
  
  "Les formules de propriétés bancaires" should "être correctement définies" in {
    val accountAvailability = LTLProperties.Banking.accountAvailability
    accountAvailability should include("has_accountAvailable_p")
  }
  
  "La conversion de formules" should "produire un string lisible" in {
    val formula = Globally(And(Atom("p"), Atom("q")))
    formula.toString should include("G")
    formula.toString should include("∧")
  }
  
  "Un parser LTL pour atomes spécialisés" should "parser les propriétés de places" in {
    val formula = LTLParser.parse("has_accountAvailable_p")
    formula shouldBe Atom("has_accountAvailable_p")
  }
  
  it should "parser les formules bancaires réalistes" in {
    val formula = LTLParser.parse("G has_accountAvailable_p")
    formula shouldBe Globally(Atom("has_accountAvailable_p"))
  }
}
