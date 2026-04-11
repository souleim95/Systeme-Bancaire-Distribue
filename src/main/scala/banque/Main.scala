package banque

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

object Main extends App {
  val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "BankingSystem")

  try {
    println("=== SYSTEME BANCAIRE DISTRIBUE ===\n")

    // Creer les comptes
    println("1. Creation des comptes...")
    val compte1 = system.systemActorOf(Compte("ACC-001", 1000.0), "Compte1")
    val compte2 = system.systemActorOf(Compte("ACC-002", 500.0), "Compte2")
    val compte3 = system.systemActorOf(Compte("ACC-003", 750.0), "Compte3")
    println("   [OK] 3 comptes crees\n")

    Thread.sleep(500)

    // Depot sur compte 1
    println("2. Depot de 200 EUR sur ACC-001...")
    compte1 ! Deposer("ACC-001", 200.0, system.ignoreRef)
    Thread.sleep(500)

    // Retrait sur compte 2
    println("3. Retrait de 100 EUR sur ACC-002...")
    compte2 ! Retirer("ACC-002", 100.0, system.ignoreRef)
    Thread.sleep(500)

    // Virement de compte 1 a compte 2
    println("4. Virement de 300 EUR de ACC-001 vers ACC-002...")
    compte1 ! Virement("ACC-001", 300.0, "ACC-002", compte2, system.ignoreRef)
    Thread.sleep(500)

    // Virement de compte 2 a compte 3
    println("5. Virement de 150 EUR de ACC-002 vers ACC-003...")
    compte2 ! Virement("ACC-002", 150.0, "ACC-003", compte3, system.ignoreRef)
    Thread.sleep(1000)

    println("\n=== ETAT FINAL DES COMPTES ===\n")

    println("Etat final (calcul):")
    println("  ACC-001: 1000 + 200 - 300 = 900 EUR")
    println("  ACC-002: 500 - 100 + 300 - 150 = 550 EUR")
    println("  ACC-003: 750 + 150 = 900 EUR")

    println("\n=== PROGRAMME TERMINE ===")

  } finally {
    system.terminate()
  }
}
