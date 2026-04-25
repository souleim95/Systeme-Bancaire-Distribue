# État de l’art — Vérification formelle, réseaux de Pétri et systèmes distribués critiques

## 1) Pourquoi la vérification formelle pour les systèmes critiques

Les systèmes distribués critiques (banque, santé, industrie, télécom) doivent garantir des propriétés de sûreté et de vivacité même en présence de concurrence, d’asynchronisme et de fautes partielles.

La **vérification formelle** consiste à exprimer mathématiquement le comportement d’un système et ses propriétés attendues, puis à prouver automatiquement (ou semi-automatiquement) que ces propriétés sont respectées.

### Propriétés visées
- **Sûreté (safety)** : « quelque chose de mauvais n’arrive jamais ».
  - Exemple bancaire : un compte ne devient jamais négatif.
- **Vivacité (liveness)** : « quelque chose de bon finit par arriver ».
  - Exemple bancaire : une transaction initiée finit par réussir ou échouer explicitement.
- **Absence de deadlock** : le système ne reste pas bloqué dans un état sans progression.

## 2) Réseaux de Pétri comme modèle de concurrence

Les réseaux de Pétri sont un modèle adapté aux systèmes distribués car ils représentent naturellement :
- les **états** du système via les places,
- les **événements/transitions** via les transitions,
- la **concurrence, causalité et conflit** via la structure du réseau,
- l’**évolution d’état** via le marquage (jetons).

Dans ce projet, ils servent à modéliser les opérations bancaires critiques (dépôt, retrait, virement), puis à explorer l’espace d’états pour vérifier :
- la franchissabilité des transitions,
- la réachabilité des marquages,
- l’absence de deadlocks,
- la cohérence des séquences d’opérations.

### Intérêt méthodologique
Contrairement à une simulation seule, la modélisation par réseau de Pétri permet une analyse systématique de nombreux chemins d’exécution possibles, incluant des interleavings concurrents difficiles à reproduire manuellement.

## 3) Logique temporelle (LTL) pour exprimer les propriétés

La **LTL (Linear Temporal Logic)** permet d’exprimer des propriétés sur l’évolution temporelle des exécutions :
- **G φ** : φ est toujours vrai,
- **F φ** : φ sera vrai un jour,
- **X φ** : φ est vrai au prochain état,
- **φ U ψ** : φ reste vrai jusqu’à ψ.

Pour un système bancaire distribué, cela permet de formaliser précisément :
- des contraintes métier stables,
- des garanties de progression,
- des comportements attendus sur des traces longues.

## 4) Positionnement du projet

Le projet combine trois niveaux complémentaires :
1. **Simulation opérationnelle Akka/Scala** : comportement des acteurs en conditions réalistes.
2. **Modèle formel Pétri** : vision abstraite et exhaustive des transitions critiques.
3. **Vérification LTL** : expression et vérification de propriétés de sûreté/vivacité.

Cette approche réduit l’écart entre comportement implémenté et garanties formelles.

## 5) Limites connues (état de l’art appliqué)

- L’explosion combinatoire de l’espace d’états reste un défi dès que le nombre d’acteurs/transactions augmente.
- Les propriétés très complexes peuvent nécessiter des optimisations (réductions, abstractions, BDD, etc.).
- La qualité de la vérification dépend de la fidélité entre le modèle formel et l’implémentation réelle.

## 6) Références bibliographiques

1. Christel Baier, Joost-Pieter Katoen, **Principles of Model Checking**, MIT Press, 2008.
2. Wolfgang Reisig, **Understanding Petri Nets**, Springer, 2013.
3. E. M. Clarke, O. Grumberg, D. Peled, **Model Checking**, MIT Press, 1999.
4. Leslie Lamport, “Proving the Correctness of Multiprocess Programs”, *IEEE TSE*, 1977.
5. Amir Pnueli, “The Temporal Logic of Programs”, *FOCS*, 1977.
6. Documentation Akka Typed : https://doc.akka.io/docs/akka/current/typed/
7. Documentation Scala : https://docs.scala-lang.org/

## 7) Méthodologie retenue pour ce projet

- Définir les invariants métier prioritaires.
- Construire le modèle acteur (Akka) correspondant aux flux critiques.
- Traduire les scénarios critiques en réseau de Pétri.
- Vérifier les propriétés structurelles et temporelles (LTL).
- Comparer les résultats de simulation et de vérification formelle.

Cette séquence assure une traçabilité entre exigences métier, implémentation distribuée et preuves formelles.
