# Systeme-Bancaire-Distribue
Modélisation et vérification formelle d'un système bancaire distribué critique avec Akka, Scala et Réseaux de Pétri.
# Modélisation et Vérification d'un Système Bancaire Distribué

**CY Tech - Projet 2026**
**Groupe 5**

Ce projet vise à modéliser et vérifier un système distribué critique (gestion sécurisée de virements bancaires) utilisant Akka et Scala, en recourant aux réseaux de Pétri pour l'analyse formelle. L'objectif principal est de garantir la fiabilité du système, l'absence de deadlocks lors de transactions concurrentes et le respect des invariants métier.

## 👥 L'Équipe (Groupe 5)
* **Souleim**
* **Mehdi**
* **Abbes**
* **Nahel**

## 🎯 Objectifs du Projet

Conformément aux exigences du projet, notre travail s'articule autour de 5 grands axes :

1. **État de l'art :** Étude de la vérification formelle pour les systèmes critiques et modélisation par réseaux de Pétri.
2. **Modélisation fonctionnelle et concurrente :** Définition de l'architecture bancaire sous forme d'acteurs Akka et identification des flux de messages critiques.
3. **Traduction vers un modèle formel :** Construction d'un réseau de Pétri modélisant notre application et capture de l'espace d'états. *Note : L'utilisation d'outils logiciels externes pour les réseaux de Pétri est strictement interdite, un analyseur maison est développé.*
4. **Vérification de propriétés :** Validation des transitions, preuve d'absence de deadlocks, et utilisation de la logique temporelle linéaire (LTL) pour exprimer et vérifier les propriétés de sûreté et de vivacité. L'invariant principal vérifié est : *Un compte ne peut jamais avoir un solde négatif*.
5. **Simulation et validation :** Simulation du système Akka/Scala pour observer le comportement réel et comparaison avec les résultats du réseau de Pétri.

## 🛠️ Technologies Utilisées
* **Langage :** Scala
* **Concurrence & Distribution :** Akka (Acteurs)
* **Modélisation Mathématique :** Réseaux de Pétri 
* **Logique Formelle :** LTL (Linear Temporal Logic) 

## 📦 Livrables
1. Bibliographie et sources de référence.
2. Modèle Akka/Scala fonctionnel simulant le système.
3. Réseau de Pétri représentant l'application.
4. Rapport détaillé de vérification.
5. Comparaison Simulation vs Modèle formel.
