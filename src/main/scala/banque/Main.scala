package banque

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

object Main extends App {
  val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "BankingSystem")

  try {
    println("=== SYSTEME BANCAIRE DISTRIBUE ===\n")

    // Creer la banque principale
    println("1. Creation de la banque...")
    val banque = system.systemActorOf(Banque(), "MainBank")
    println("   [OK] Banque creee\n")

    Thread.sleep(500)

    // Creer les comptes via la banque
    println("2. Creation des comptes via la banque...")
    banque ! Banque.CreerCompteReq("ACC-001", 1000.0, system.ignoreRef)
    banque ! Banque.CreerCompteReq("ACC-002", 500.0, system.ignoreRef)
    banque ! Banque.CreerCompteReq("ACC-003", 750.0, system.ignoreRef)
    println("   [OK] 3 comptes crees\n")

    Thread.sleep(500)

    // Opérations via la banque
    println("3. Depot de 200 EUR sur ACC-001...")
    banque ! Banque.OperationCompteBanque(
      Deposer("ACC-001", 200.0, system.ignoreRef)
    )
    Thread.sleep(500)

    println("4. Retrait de 100 EUR sur ACC-002...")
    banque ! Banque.OperationCompteBanque(
      Retirer("ACC-002", 100.0, system.ignoreRef)
    )
    Thread.sleep(500)

    println("5. Virement de 300 EUR de ACC-001 vers ACC-002...")
    banque ! Banque.OperationCompteBanque(
      Virement("ACC-001", 300.0, "ACC-002", system.ignoreRef, system.ignoreRef)
    )
    Thread.sleep(500)

    println("6. Virement de 150 EUR de ACC-002 vers ACC-003...")
    banque ! Banque.OperationCompteBanque(
      Virement("ACC-002", 150.0, "ACC-003", system.ignoreRef, system.ignoreRef)
    )
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
