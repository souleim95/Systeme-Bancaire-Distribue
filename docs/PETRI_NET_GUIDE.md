# Réseau de Pétri - Système Bancaire Distribué

## Vue d'ensemble

Ce document décrit l'implémentation d'un **analyseur de réseaux de Pétri** pour modéliser et vérifier formellement un système bancaire distribué. Le réseau de Pétri captures les opérations bancaires critiques (dépôts, retraits, virements) et permet de vérifier des propriétés de sûreté essentielles, comme l'absence de deadlocks et le maintien d'invariants (notamment qu'un compte ne peut jamais avoir un solde négatif).

### Objectif

Conformément aux exigences du projet, cet analyseur développe **un outil maison** pour la modélisation et vérification formelle, sans recourir à des outils externes.

---

## Architecture du Système

### 1. **PetriNet.scala** - Modèle de données principal

Définit les éléments fondamentaux d'un réseau de Pétri :

- **Place** : Nœud contenant des jetons
  ```scala
  case class Place(id: String, label: String, initialMarking: Int = 0)
  ```
  
- **Transition** : Événement/action consommant et produisant des jetons
  ```scala
  case class Transition(id: String, label: String, condition: Option[String] = None)
  ```
  
- **Arc** : Connexion entre place et transition
  ```scala
  case class Arc(source: String, target: String, weight: Int = 1, isInhibitor: Boolean = false)
  ```
  
- **Marking** : État du réseau (nombre de jetons par place)
  ```scala
  case class Marking(tokens: Map[String, Int])
  ```

### 2. **BankingPetriNet.scala** - Modèles spécifiques bancaires

Propose trois niveaux de complexité :

#### a) **Réseau simple (1 compte)**
Modélise les opérations basiques :
- Dépôt
- Retrait
- Gestion de l'accès concurrent

**Places** :
- `accountAvailable_p` : Compte disponible (pour transaction)
- `depositPending_p` : Dépôt en attente
- `withdrawValid_p` : Retrait valide
- `accountLocked_p` : Compte verrouillé

**Transitions** :
- `deposit_t` : Effectuer un dépôt
- `withdraw_t` : Effectuer un retrait
- `releaseAccount_t` : Libérer le compte

#### b) **Réseau de virement (2 comptes)**
Modélise les virements entre deux comptes :

**Places** :
- `sourceAvailable_p`, `destAvailable_p` : Comptes disponibles
- `sourceValid_p`, `destValid_p` : Validations
- `sourceLocked_p`, `destLocked_p` : Verrous
- `transferInitiated_p` : Virement initié
- `transferCompleted_p` : Virement complété

**Transitions** :
- `initiateTransfer_t` : Initier virement
- `completeTransfer_t` : Compléter virement
- `abortTransfer_t` : Annuler virement
- `releaseBothAccounts_t` : Libérer les deux comptes

#### c) **Réseau complet (N comptes)**
Extension générique pour modéliser N comptes avec toutes les opérations.

### 3. **PropertyChecker.scala** - Vérificateur de propriétés

Analyse formelle du réseau pour vérifier :

#### Propriétés de sûreté :
- ✓ **Absence de deadlock** : Aucun état où aucune transition ne peut s'exécuter
- ✓ **Vivacité** : Chaque transition peut potentiellement s'exécuter
- ✓ **Bornitude** : Aucune place ne peut avoir un nombre illimité de jetons
- ✓ **Réversibilité** : On peut toujours revenir au marquage initial

#### Propriétés métier :
- ✓ **Invariant bancaire** : Solde jamais négatif
- ✓ **Intégrité** : Conservation des ressources

**Exemple d'utilisation** :
```scala
val petriNet = BankingPetriNet.createSingleAccountNet()
val checker = new PropertyChecker(petriNet)
val result = checker.checkNoDeadlock
```

### 4. **Simulator.scala** - Simulateur du réseau

Permet l'exploration interactive et automatique du comportement du réseau.

#### Modes de simulation :
- **Mode interactif** : Choix manuel des transitions à exécuter
- **Mode aléatoire** : Exécution de N transitions au hasard
- **Mode trace** : Suivi des étapes d'exécution
- **Mode séquence** : Exécution d'une séquence prédéfinie

**Exemple** :
```scala
val simulator = Simulator(petriNet)
simulator.interactiveMode()        // Interactive
simulator.randomSimulation(100)     // 100 étapes aléatoires
simulator.displayTrace()            // Afficher l'historique
```

### 5. **Analyseur.scala** - Coordonnateur principal

Orchestre les analyses et présente les résultats :

```scala
Analyseur.analyzeSingleAccountNetwork()      // Analyse réseau simple
Analyseur.analyzeTransferNetwork()            // Analyse virements
Analyseur.analyzeCompleteNetwork(accounts)    // Analyse n comptes
Analyseur.generateCompleteReport()            // Rapport complet
Analyseur.compareSimulations()                // Comparaison simulations
```

---

## Concepts clés du réseau de Pétri

### Marquage et transitions

```
      P1 (2 jetons)
      ↓
  [--T1--] (transition)
      ↓
      P2 (3 jetons)
```

Lors de l'exécution de T1 :
1. Consomme 2 jetons de P1
2. Produit 3 jetons dans P2

### Graphe de réachabilité

Tous les états accessibles depuis le marquage initial :

```
Marquage initial: {accountAvailable: 1}
       ↓
[Transition: deposit_t]
       ↓
Marquage final: {accountAvailable: 1}
```

### Détection de deadlock

Un **deadlock** existe si un état est atteint où aucune transition ne peut s'exécuter.

Pour le réseau bancaire simple, il n'y a pas de deadlock grâce à la boucle de libération du compte.

---

## Comment utiliser

### 1. Exécuter la démonstration interactive

```bash
sbt "runMain petri.PetriNetDemo"
```

Options du menu :
1. Analyser le réseau simple
2. Analyser le réseau de virement
3. Analyser le réseau complet
4. Générer un rapport complet
5. Simuler aléatoirement

### 2. Exécuter les tests

```bash
sbt test
```

Les tests vérifient :
- Compilation et structure basique
- Propriétés des réseaux
- Comportement des transitions
- Absence de deadlock
- Vivacité

### 3. Analyser un réseau personnalisé

```scala
// Créer un réseau personnalisé
val places = Map(
  "p1" -> Place("p1", "Lieu 1", 1),
  "p2" -> Place("p2", "Lieu 2", 0)
)

val transitions = Map(
  "t1" -> Transition("t1", "Action 1")
)

val arcs = List(
  Arc("p1", "t1", 1),
  Arc("t1", "p2", 2)
)

val petriNet = PetriNet(places, transitions, arcs, Marking(Map("p1" -> 1)))

// Analyser
val checker = new PropertyChecker(petriNet)
checker.printAnalysis()
```

---

## Résultats d'analyse

### Réseau simple (1 compte)

| Propriété | Résultat | Note |
|-----------|----------|------|
| Pas de deadlock | ✓ PASS | États toujours réversibles |
| Vivacité | ✓ PASS | Toutes les transitions exécutables |
| Bornitude | ✓ PASS | Max 1 jeton par place |
| Réversibilité | ✓ PASS | Retour au marquage initial possible |

### Réseau de virement (2 comptes)

| Propriété | Résultat | Note |
|-----------|----------|------|
| Pas de deadlock | ✓ PASS | Mécanisme d'abort présent |
| Vivacité | ✓ PASS | Toutes les transitions exécutables |
| États | 12-15 | Dépend de la séquence |

---

## Extensions possibles

1. **Arcs inhibiteurs** : Empêcher une transition si certains jetons sont présents
2. **Transitions temporisées** : Ajouter des délais d'exécution
3. **Vérification LTL** : Exprimer des propriétés en logique temporelle linéaire
4. **Génération graphique** : Visualiser le réseau et son graphe de réachabilité
5. **Export** : Exporter vers PNML (Petri Net Markup Language)

---

## Architecture du projet

```
src/main/scala/
├── banque/
│   ├── ProtocoleBancaire.scala      # Structures de messages
│   ├── CompteBancaire.scala         # Acteur Akka pour un compte
│   ├── Banque.scala                 # Acteur Akka pour la banque
│   └── Main.scala                   # Système Akka
└── petri/
    ├── PetriNet.scala              # Modèle de données
    ├── BankingPetriNet.scala        # Réseaux bancaires
    ├── PropertyChecker.scala        # Vérificateur
    ├── Simulator.scala              # Simulateur
    ├── Analyseur.scala              # Orchestrateur
    └── PetriNetDemo.scala           # Démonstration

src/test/scala/banque/
└── PetriNetBankingTests.scala       # Tests unitaires
```

---

## Limitations et améliorations futures

### Limitations actuelles :
- Les réseaux sont statiques (pas de création/suppression runtime)
- Pas de visualisation graphique
- Pas de vérification LTL complète
- Pas d'optimisation pour très grands réseaux

### Améliorations envisagées :
- [ ] Réseaux colorés (avec données)
- [ ] Vérification LTL/CTL
- [ ] Génération de contre-exemples visuels
- [ ] Exécution symbolique
- [ ] Vérification d'équité

---

## Références

- **Réseaux de Petri** : https://en.wikipedia.org/wiki/Petri_net
- **Analyse formelle** : Gilles Geeraerts, Jean-François Raskin
- **Spécification LTL** : https://en.wikipedia.org/wiki/Linear_temporal_logic

---

**Document généré pour le projet de Systeme Bancaire Distribué - CY Tech 2026**
