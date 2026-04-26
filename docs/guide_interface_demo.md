# Guide d'utilisation de l'interface de demonstration

Ce document explique comment utiliser le front local pour presenter le projet, tester les
fonctionnalites principales et montrer que les attendus du cahier des charges
`projet_2026.pdf` sont couverts.

Le front est un outil de demonstration. Les preuves techniques restent portees par le code
Scala/Akka, le modele de reseau de Petri, le model checker LTL, les tests et les rapports
du dossier `docs/`.

## 1. Lancer la demonstration

Depuis la racine du projet :

```bash
sbt "runMain banque.FrontServer 8080"
```

Puis ouvrir :

```text
http://localhost:8080
```

Etat initial charge automatiquement :

- `ACC-001` avec `1000.0 EUR`
- `ACC-002` avec `500.0 EUR`
- `ACC-003` avec `750.0 EUR`
- total banque attendu : `2250.0 EUR`

Si le port `8080` est deja utilise, lancer par exemple :

```bash
sbt "runMain banque.FrontServer 8081"
```

Puis ouvrir `http://localhost:8081`.

## 2. Lire l'ecran principal

La barre du haut contient les actions globales :

- `Actualiser` recharge l'etat courant de la banque et les analyses formelles.
- `Reinitialiser demo` remet les trois comptes de depart et le total a `2250.0 EUR`.

Les indicateurs affichent :

- `Total banque` : somme des soldes des comptes actifs.
- `Comptes actifs` : nombre de comptes connus par l'acteur `Banque`.
- `Etats Petri` : taille de l'espace d'etats genere par le modele formel.
- `Verification` : resultat global des proprietes Petri et LTL affichees dans le front.

Le panneau `Operations` permet de tester le comportement Akka :

- creation de compte ;
- depot ;
- retrait ;
- virement entre deux comptes.

Le panneau `Comptes` affiche les comptes actifs, leurs soldes et les actions disponibles :

- consulter l'historique ;
- fermer un compte.

Le panneau `Analyse formelle` affiche les resultats du reseau de Petri :

- nombre de places ;
- nombre de transitions ;
- nombre d'arcs ;
- nombre d'etats atteignables ;
- absence de deadlock ;
- vivacite ;
- bornitude ;
- invariant des marquages non negatifs ;
- formules LTL predefinies.

Le panneau `Verifier LTL` permet de saisir une formule LTL et de la verifier sur le
modele formel.

## 3. Parcours conseille pour la presentation

### Etape 1 - Revenir a l'etat initial

Cliquer sur `Reinitialiser demo`.

Verifier :

- `Comptes actifs = 3`
- `Total banque = 2250.00 EUR`
- `Etats Petri = 216`
- `Verification = PASS`

Message a dire pendant la presentation :

> Le front pilote le systeme Akka reel. En parallele, il expose les resultats du modele
> formel Petri/LTL pour montrer que le comportement critique est couvert.

### Etape 2 - Montrer la creation de compte

Dans `Creer un compte` :

- identifiant : `ACC-004`
- solde initial : `250`
- cliquer sur `Creer`

Resultat attendu :

- le compte `ACC-004` apparait dans la liste ;
- le nombre de comptes passe a `4` ;
- le total banque passe a `2500.00 EUR`.

Point du cahier des charges couvert :

- modele Akka/Scala fonctionnel ;
- acteur `Banque` qui cree et supervise les comptes ;
- routage des messages critiques.

### Etape 3 - Montrer un depot valide

Dans `Depot` :

- compte : `ACC-001`
- montant : `100`
- cliquer sur `Deposer`

Resultat attendu :

- le solde de `ACC-001` augmente de `100` ;
- le total banque augmente de `100` ;
- une reponse de succes est affichee.

Point couvert :

- simulation du comportement reel Akka ;
- operation critique simple ;
- mise a jour d'etat actorisee.

### Etape 4 - Montrer un retrait valide

Dans `Retrait` :

- compte : `ACC-002`
- montant : `50`
- cliquer sur `Retirer`

Resultat attendu :

- le solde de `ACC-002` diminue de `50` ;
- le total banque diminue de `50` ;
- l'historique du compte contient l'operation.

Point couvert :

- verification de la garde metier `solde >= montant` ;
- coherence des sequences d'operations.

### Etape 5 - Montrer la protection contre un solde negatif

Dans `Retrait` :

- compte : `ACC-002`
- montant : `999999`
- cliquer sur `Retirer`

Resultat attendu :

- l'operation est refusee ;
- le solde de `ACC-002` ne change pas ;
- le total banque ne change pas.

Point couvert :

- invariant metier principal : un compte ne peut jamais avoir un solde negatif ;
- refus explicite des operations invalides dans le systeme Akka ;
- coherence avec le modele Petri, dont les marquages ne deviennent jamais negatifs.

### Etape 6 - Montrer un virement entre deux comptes

Dans `Virement` :

- source : `ACC-001`
- destination : `ACC-002`
- montant : `100`
- cliquer sur `Virer`

Resultat attendu :

- `ACC-001` diminue de `100` ;
- `ACC-002` augmente de `100` ;
- le total banque reste identique ;
- la reponse indique un virement confirme.

Point couvert :

- flux de messages critique entre acteurs ;
- debit source, credit destination, confirmation ;
- absence de perte de fonds pendant un virement.

### Etape 7 - Consulter l'historique

Dans la carte d'un compte, cliquer sur `Historique`.

Resultat attendu :

- les transactions du compte sont affichees ;
- les depots, retraits et virements deja effectues sont visibles.

Point couvert :

- observabilite de la simulation Akka ;
- trace des operations executees.

### Etape 8 - Fermer un compte

Sur `ACC-004`, cliquer sur `Fermer`.

Resultat attendu :

- le compte disparait de la liste des comptes actifs ;
- son solde est restitue dans la reponse de fermeture ;
- les operations futures sur cet identifiant sont refusees par la banque.

Point couvert :

- gestion d'etats de compte ;
- refus des operations sur un compte ferme.

### Etape 9 - Relancer l'analyse formelle

Dans `Analyse formelle`, cliquer sur `Relancer`.

Verifier que les proprietes restent en succes :

- `No Deadlock` ;
- `Liveness` ;
- `Boundedness` ;
- `Marquages non negatifs`.

Point couvert :

- reseau de Petri construit sans outil externe ;
- generation de l'espace d'etats ;
- verification des proprietes structurelles ;
- verification des invariants metier.

## 4. Formules LTL utiles a montrer

Dans le panneau `Verifier LTL`, saisir ces formules une par une.

```text
G enabled
```

Interpretation :

- globalement, il existe toujours au moins une transition active ;
- sert a montrer l'absence de blocage global.

Resultat attendu : `PASS`.

```text
G !deadlock
```

Interpretation :

- aucun etat atteignable ne doit etre un deadlock.

Resultat attendu : `PASS`.

```text
G (has_ACC-001_available | has_ACC-001_locked)
```

Interpretation :

- le compte `ACC-001` reste toujours dans un etat coherent : disponible ou verrouille
  pendant une operation.

Resultat attendu : `PASS`.

```text
G (has_ACC-002_available | has_ACC-002_locked)
```

Interpretation :

- meme verification de coherence pour `ACC-002`.

Resultat attendu : `PASS`.

Ces formules montrent que le model checker LTL maison sait verifier des proprietes de
surete et de vivacite sur l'evolution du reseau de Petri.

## 5. Correspondance stricte avec le cahier des charges

| Attendu du PDF | Ou le montrer dans le projet | Comment le montrer avec le front |
| --- | --- | --- |
| Etat de l'art sur verification formelle et reseaux de Petri | `docs/etat_de_l_art.md` | Mentionner le document pendant l'introduction |
| Application distribuee critique | `src/main/scala/banque` | Manipuler les comptes et les virements |
| Architecture Akka | `Banque`, `Compte`, `ProtocoleBancaire` | Creation, depot, retrait, virement |
| Flux de messages critiques | `CompteBancaire.scala`, `Banque.scala` | Montrer le virement source -> destination -> confirmation |
| Reseau de Petri sans outil externe | `src/main/scala/petri` | Panneau `Analyse formelle` |
| Espace d'etats | `PetriNet.getReachabilityGraph` | Indicateur `Etats Petri = 216` |
| Absence de deadlock | `PropertyChecker.checkNoDeadlock`, LTL `G !deadlock` | `No Deadlock` et formule `G !deadlock` |
| Vivacite | `PropertyChecker.checkLiveness`, LTL | `Liveness` dans l'analyse formelle |
| Bornitude | `PropertyChecker.checkBoundedness` | `Boundedness` dans l'analyse formelle |
| Invariant solde jamais negatif | gardes Akka + marquages Petri non negatifs | Retrait impossible de `999999` + invariant `Marquages non negatifs` |
| Simulation Akka | tests + front + `PetriNetIntegrationDemo` | Operations executees dans le front |
| Comparaison Akka vs Petri | `docs/rapport_verification.md` et demo d'integration | Montrer operations Akka puis resultats Petri/LTL |
| Sources bibliographiques | `docs/etat_de_l_art.md` | Indiquer le fichier dans les livrables |
| Lien GitHub | `README.md` | Montrer le repository et la branche `souleim_v2` |

## 6. Commandes finales a executer avant rendu

Compilation et tests :

```bash
sbt clean test
```

Demonstration comparee Akka/Petri :

```bash
sbt "runMain banque.PetriNetIntegrationDemo"
```

Verification LTL complete :

```bash
sbt "runMain banque.LTLIntegrationExample"
```

Front de presentation :

```bash
sbt "runMain banque.FrontServer 8080"
```

Verification rapide par API :

```bash
curl http://localhost:8080/api/state
curl http://localhost:8080/api/petri
curl "http://localhost:8080/api/ltl?formula=G+enabled"
```

Resultats attendus :

- tests Scala : tous les tests passent ;
- Petri : pas de deadlock, vivacite OK, bornitude OK ;
- LTL : formules de demonstration en `PASS` ;
- front : disponible sur `http://localhost:8080`.

## 7. Conseils pour l'oral

Ordre recommande :

1. Presenter le probleme : systeme bancaire distribue critique.
2. Montrer l'architecture Akka : `Banque`, `Compte`, messages.
3. Executer deux operations simples dans le front : depot et retrait.
4. Executer un virement pour montrer l'interaction critique entre comptes.
5. Tenter un retrait trop grand pour prouver la protection du solde.
6. Montrer le panneau `Analyse formelle`.
7. Verifier `G enabled` et `G !deadlock` dans le panneau LTL.
8. Conclure avec les tests `sbt clean test` et les documents de verification.

Phrase de conclusion possible :

> Le front permet de manipuler le systeme Akka reel, tandis que le panneau Petri/LTL
> expose la verification formelle du modele critique. Les tests et le rapport complet
> ferment la boucle entre simulation, modele formel et exigences du cahier des charges.
