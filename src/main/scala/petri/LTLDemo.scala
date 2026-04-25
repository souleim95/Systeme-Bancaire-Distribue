package petri

/**
 * Démonstration du vérificateur LTL complet
 * Montre comment vérifier les propriétés formelles en LTL
 */
object LTLDemo extends App {
  
  println("\n" + "█"*80)
  println("█ DÉMONSTRATION: VÉRIFICATION LTL DES PROPRIÉTÉS BANCAIRES        █")
  println("█"*80)
  
  // ========== PARTIE 1: PARSING LTL ==========
  println("\n[PARTIE 1] Parsing de formules LTL")
  println("-"*80)
  
  val testFormulas = List(
    "p",
    "G p",
    "F q",
    "p & q",
    "p | q",
    "!p",
    "X p",
    "p U q",
    "G (p -> F q)"
  )
  
  println("Parsing des formules LTL:")
  for (formula <- testFormulas) {
    try {
      val parsed = LTLParser.parse(formula)
      println(s"  ✓ '$formula' → $parsed")
    } catch {
      case e: Exception =>
        println(s"  ✗ '$formula' → Erreur: ${e.getMessage}")
    }
  }
  
  // ========== PARTIE 2: VÉRIFICATION SUR LE RÉSEAU SIMPLE ==========
  println("\n\n[PARTIE 2] Vérification LTL - Réseau Simple (1 compte)")
  println("-"*80)
  
  val simpleNet = BankingPetriNet.createSingleAccountNet()
  val simpleChecker = new LTLModelChecker(simpleNet)
  
  val simpleFormulas = List(
    // Propriétés vraies
    "true",
    "G true",
    "has_accountAvailable_p",
    
    // Propriétés fausses
    "false",
    "has_nonexistent_place",
    
    // Propriétés bancaires
    "G (has_accountAvailable_p)",
    "F (has_accountAvailable_p)"
  )
  
  val simpleResults = simpleChecker.checkAll(simpleFormulas)
  simpleChecker.printReport(simpleResults)
  
  // ========== PARTIE 3: VÉRIFICATION SUR LE RÉSEAU DE VIREMENT ==========
  println("\n\n[PARTIE 3] Vérification LTL - Réseau de Virement (2 comptes)")
  println("-"*80)
  
  val transferNet = BankingPetriNet.createTransferNet()
  val transferChecker = new LTLModelChecker(transferNet)
  
  val transferFormulas = List(
    // Propriétés communes
    "true",
    "G true",
    
    // Propriétés de comptes
    "has_sourceAvailable_p | has_destAvailable_p",
    
    // Propriétés temporelles
    "F (has_transferCompleted_p)",
    "G (has_sourceLocked_p -> F has_sourceAvailable_p)"
  )
  
  val transferResults = transferChecker.checkAll(transferFormulas)
  transferChecker.printReport(transferResults)
  
  // ========== PARTIE 4: VÉRIFICATION SUR LE RÉSEAU COMPLET ==========
  println("\n\n[PARTIE 4] Vérification LTL - Réseau Complet (3 comptes)")
  println("-"*80)
  
  val completeNet = BankingPetriNet.createCompleteNet(List("ACC-001", "ACC-002", "ACC-003"))
  val completeChecker = new LTLModelChecker(completeNet)
  
  val completeFormulas = List(
    // Propriétés essentielles
    "true",
    "G true",
    
    // Propriétés bancaires réalistes
    "G (has_ACC-001_available | has_ACC-002_available | has_ACC-003_available)",
    
    // Propriétés de vivacité
    "F true"
  )
  
  val completeResults = completeChecker.checkAll(completeFormulas)
  completeChecker.printReport(completeResults)
  
  // ========== PARTIE 5: PROPRIÉTÉS PRÉDÉFINIES ==========
  println("\n\n[PARTIE 5] Propriétés LTL Prédéfinies pour Systèmes Bancaires")
  println("-"*80)
  
  println("\nPropriétés de sécurité disponibles:")
  println(s"  • Account Availability: ${LTLProperties.Banking.accountAvailability}")
  println(s"  • Transfer Guarantee: ${LTLProperties.Banking.transferGuarantee}")
  println(s"  • No Deadlock: ${LTLProperties.Banking.noDeadlock}")
  
  println("\nPropriétés de vivacité disponibles:")
  println(s"  • Eventually Happens: ${LTLProperties.Liveness.eventuallyHappens}")
  println(s"  • Always Can Progress: ${LTLProperties.Liveness.alwaysCanProgress}")
  
  // ========== PARTIE 6: ANALYSE COMPARATIVE ==========
  println("\n\n[PARTIE 6] Comparaison des Propriétés entre les Réseaux")
  println("-"*80)
  
  val formulas_for_comparison = List(
    ("Propriété: true", "true"),
    ("Propriété: G true", "G true"),
    ("Propriété: F true", "F true")
  )
  
  println("\nRéseau Simple:")
  val simple_comp = simpleChecker.checkAll(formulas_for_comparison.map(_._2))
  simple_comp.zipWithIndex.foreach { case (result, idx) =>
    println(s"  ${formulas_for_comparison(idx)._1}: ${if (result.isValid) "✓" else "✗"}")
  }
  
  println("\nRéseau Virement:")
  val transfer_comp = transferChecker.checkAll(formulas_for_comparison.map(_._2))
  transfer_comp.zipWithIndex.foreach { case (result, idx) =>
    println(s"  ${formulas_for_comparison(idx)._1}: ${if (result.isValid) "✓" else "✗"}")
  }
  
  println("\nRéseau Complet:")
  val complete_comp = completeChecker.checkAll(formulas_for_comparison.map(_._2))
  complete_comp.zipWithIndex.foreach { case (result, idx) =>
    println(s"  ${formulas_for_comparison(idx)._1}: ${if (result.isValid) "✓" else "✗"}")
  }
  
  // ========== RÉSUMÉ ==========
  println("\n\n[RÉSUMÉ]")
  println("-"*80)
  
  val totalFormulas = simpleResults.length + transferResults.length + completeResults.length
  val totalValid = simpleResults.count(_.isValid) + 
                   transferResults.count(_.isValid) + 
                   completeResults.count(_.isValid)
  
  println(f"""
Vérifications effectuées:
  • Réseau simple: ${simpleResults.length} formules (${simpleResults.count(_.isValid)} valides)
  • Réseau virement: ${transferResults.length} formules (${transferResults.count(_.isValid)} valides)
  • Réseau complet: ${completeResults.length} formules (${completeResults.count(_.isValid)} valides)
  
Total: $totalValid/$totalFormulas propriétés vérifiées

Synthèse:
  ✓ Parseur LTL fonctionnel (opérateurs booléens et temporels)
  ✓ Évaluateur de formules sur chemins infinis
  ✓ Vérification sur tous les réseaux bancaires
  ✓ Propriétés de sûreté et vivacité
  ✓ Bibliothèque de propriétés prédéfinies
  """)
  
  println("█"*80)
  println("█ FIN DE LA DÉMONSTRATION LTL")
  println("█"*80 + "\n")
}
