# Rapport de verification formelle

Ce document relie explicitement le cahier des charges `projet_2026.pdf` a l'implementation Scala/Akka et au modele formel maison.

## 1. Perimetre du systeme critique

Application choisie : systeme bancaire distribue.

Acteurs Akka :
- `Banque` : supervise la creation, la suppression et le routage des operations vers les comptes.
- `Compte` : porte l'etat d'un compte, son solde, son historique et les virements en attente.
- `ProtocoleBancaire` : definit les commandes et reponses echangees.

Flux critiques :
- Depot : `Banque -> Compte.Deposer -> Banque -> client`.
- Retrait : `Banque -> Compte.Retirer -> Banque -> client`.
- Virement : `Banque -> Compte source -> Compte destination -> Compte source -> Banque -> client`.

Le virement reserve le montant sur le compte source, attend la confirmation du destinataire, puis confirme ou rembourse la source en cas d'echec.

## 2. Modele reseau de Petri

Implementation : `src/main/scala/petri`.

Elements couverts :
- Places : disponibilite des comptes, validite abstraite du solde, verrous d'operation, virements en attente.
- Transitions : depot, retrait, liberation de compte, initiation/liberation de virement.
- Arcs : consommation/production de jetons, avec support des arcs inhibiteurs.
- Espace d'etats : genere par parcours BFS dans `PetriNet.getReachabilityGraph`.

Resultats observes :
- Reseau simple : 2 etats atteignables.
- Reseau de virement : 3 etats atteignables.
- Reseau complet a 3 comptes : 15 places, 21 transitions, 216 etats atteignables.

## 3. Proprietes structurelles verifiees

Verificateur : `PropertyChecker`.

Proprietes :
- Absence de deadlock : aucun marquage atteignable sans transition active.
- Vivacite stricte : depuis chaque marquage atteignable, chaque transition peut redevenir executable apres une sequence finie.
- Bornitude : aucun nombre de jetons ne depasse la borne analysee.
- Reversibilite : le marquage initial est atteignable depuis les etats explores quand le modele le garantit.

Etat actuel :
- `createSingleAccountNet` : deadlock absent, vivant, borne, reversible.
- `createTransferNet` : deadlock absent, vivant, borne, reversible.
- `createCompleteNet(List("ACC-001", "ACC-002", "ACC-003"))` : deadlock absent, vivant, borne, 216 etats atteignables.

## 4. Invariants metier

Invariant principal : un compte ne doit jamais avoir un solde negatif.

Dans Akka :
- Creation refusee si `soldeInitial < 0`.
- Depot refuse si `montant <= 0`.
- Retrait refuse si `montant <= 0` ou `solde < montant`.
- Virement refuse si `montant <= 0` ou `solde < montant`.
- Virement vers destinataire ferme : le debit reserve est rembourse.

Dans le reseau de Petri :
- Les marquages sont des entiers naturels.
- `fireTransition` ne franchit une transition que si les places d'entree contiennent assez de jetons.
- `Marking.decrement` ne produit jamais de valeur negative.

Tests associes :
- `CompteSpec` verifie les refus de retrait/virement insuffisant et le remboursement si le destinataire est ferme.
- `PetriNetBankingTests` verifie l'absence de marquage negatif et les proprietes structurelles.

## 5. Verification LTL

Implementation : `LTLParser`, `LTLEvaluator`, `LTLModelChecker`.

Operateurs supportes :
- Booleens : `!`, `&`, `|`, `->`, ainsi que `¬`, `∧`, `∨`, `→`.
- Temporels : `X`, `F`, `G`, `U`, `R`.

Semantique :
- Les chemins sont representes par des lassos : prefixe fini + cycle.
- Les deadlocks sont representes par une boucle stutter sur l'etat bloque.
- Le model checker retourne un contre-exemple quand une formule est violee.

Formules utiles :
- Absence de deadlock : `G enabled`.
- Etat bloque interdit : `G !deadlock`.
- Garantie de virement : `G (has_transferInitiated_p -> F (has_transferCompleted_p | has_sourceAvailable_p))`.
- Liberation des verrous : `G (has_sourceLocked_p -> F has_sourceAvailable_p)`.

## 6. Simulation comparee Akka vs Petri

Scenario Akka de reference :
1. Creation `ACC-001 = 1000`, `ACC-002 = 500`, `ACC-003 = 750`.
2. Depot de 200 sur `ACC-001`.
3. Retrait de 100 sur `ACC-002`.
4. Virement de 300 de `ACC-001` vers `ACC-002`.

Soldes attendus :
- `ACC-001 = 900`.
- `ACC-002 = 700`.
- `ACC-003 = 750`.

Correspondance Petri :
- Depot : transition de depot puis liberation du compte.
- Retrait : transition de retrait gardee par la place de validite.
- Virement : transition de transfert puis liberation du transfert.

La demo `banque.PetriNetIntegrationDemo` execute l'analyse formelle, la simulation Petri et le scenario Akka dans le meme flux.

## 7. Livrables du PDF

1. Sources bibliographiques : `docs/etat_de_l_art.md`.
2. Modele Akka/Scala fonctionnel : `src/main/scala/banque`.
3. Reseau de Petri : `src/main/scala/petri/BankingPetriNet.scala`.
4. Rapport de verification : ce document + `docs/PETRI_NET_GUIDE.md` + `docs/LTL_GUIDE.md`.
5. Simulation comparee : `src/main/scala/banque/PetriNetIntegrationDemo.scala`.
6. Lien GitHub : indique dans `README.md`.

## 8. Commandes de validation

```bash
sbt test
sbt "runMain banque.PetriNetIntegrationDemo"
sbt "runMain banque.LTLIntegrationExample"
```
