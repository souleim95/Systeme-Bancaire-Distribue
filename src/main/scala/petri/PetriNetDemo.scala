package petri

/**
 * Programme de démonstration du réseau de Pétri bancaire
 */
object PetriNetDemo extends App {
  
  println("\n" + "█"*70)
  println("█ SYSTÈME BANCAIRE DISTRIBUTÉ - ANALYSE PAR RÉSEAUX DE PÉTRI █")
  println("█"*70)
  
  // Menu de sélection
  println("\nQue voulez-vous faire?")
  println("1. Analyser le réseau simple (1 compte)")
  println("2. Analyser le réseau de virement (2 comptes)")
  println("3. Analyser le réseau complet (3 comptes)")
  println("4. Générer un rapport complet")
  println("5. Simuler aléatoirement")
  println("\nChoisissez une option et appuyez sur Enter...")
  
  val choice = scala.io.StdIn.readLine().trim
  
  choice match {
    case "1" =>
      Analyseur.analyzeSingleAccountNetwork()
      
    case "2" =>
      Analyseur.analyzeTransferNetwork()
      
    case "3" =>
      Analyseur.analyzeCompleteNetwork(List("ACC-001", "ACC-002", "ACC-003"))
      
    case "4" =>
      Analyseur.generateCompleteReport()
      
    case "5" =>
      Analyseur.compareSimulations()
      
    case _ =>
      println("Option inconnue. Génération du rapport par défaut...")
      Analyseur.generateCompleteReport()
  }
  
  println("\n" + "█"*70)
  println("█ FIN DE LA DÉMONSTRATION")
  println("█"*70 + "\n")
}
