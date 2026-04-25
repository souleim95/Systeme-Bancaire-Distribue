# 🔍 Référence Rapide des Opérateurs LTL

## Tableau récapitulatif

| Nom | Symbole | Syntaxe | Signification | Exemple |
|-----|---------|---------|---------------|---------|
| **Négation** | ¬, ! | ¬p, !p | Non p | !error |
| **Conjonction** | ∧, & | p ∧ q, p & q | p ET q | locked & waiting |
| **Disjonction** | ∨, \| | p ∨ q, p \| q | p OU q | done \| failed |
| **Prochain** | **X** | X p | p au prochain état | X done |
| **Finalement** | **F** | F p | p à un moment | F complete |
| **Globalement** | **G** | G p | p partout | G valid |
| **Jusqu'à** | **U** | p U q | p jusqu'à q | wait U complete |
| **Relâche** | **R** | p R q | q jusqu'à p | fail R recovery |

---

## Diagrammes temporels

### X (Next) - Prochain état

```
État: 0   1   2   3   4
      P: F   F   F   F   F
      
X P: vrai au temps 1 ssi P est vrai au temps 2
```

### F (Finally) - Finalement

```
État: 0   1   2   3   4
      P: F   F   T   F   F   ← vrai somewhere
      
F P: vrai à partir du temps 0
     (P sera vrai à un moment du futur)
```

### G (Globally) - Globalement

```
État: 0   1   2   3   4
      P: T   T   T   T   T   ← toujours vrai
      
G P: vrai à partir du temps 0
     (P reste vrai partout)
```

### U (Until) - Jusqu'à

```
État: 0   1   2   3   4
      P: T   T   F   F   F
      Q: F   F   T   T   F   ← Q devient vrai à 2
      
P U Q: vrai au temps 0
       (P vrai jusqu'à ce que Q devient vrai)
```

### R (Release) - Relâche

```
État: 0   1   2   3   4
      P: F   F   T   F   F
      Q: T   T   T   T   T   ← Q toujours vrai
      
P R Q: vrai au temps 0
       (Q reste vrai jusqu'à ce que P devient vrai, ou toujours vrai)
```

---

## Équivalences utiles

```
F p    ≡  true U p
G p    ≡  !F(!p)  ≡  !(F(!p))
X p    ≡  !X(!p)  (non équivalent en général)
p U q  ≡  q ∨ (p ∧ X(p U q))
p R q  ≡  q ∧ (p ∨ X(p R q))
```

---

## Propriétés de sûreté vs vivacité

### Sûreté (Safety)
"Quelque chose de mauvais ne se produit jamais"
```
G ¬error       -- Jamais d'erreur
G (p → ¬q)     -- Si p alors jamais q
G ¬(p ∧ q)     -- Jamais p et q ensemble
```

### Vivacité (Liveness)
"Quelque chose de bon se produit finalement"
```
F done         -- Finalement complété
G (p → F q)    -- Si p alors finalement q
F G p          -- Finalement p reste vrai
```

---

## Formules courantes pour systèmes bancaires

### Prévention de deadlock
```
G (X true)     -- Toujours une transition possible
F true         -- Quelque chose se produit
```

### Atomicité des transactions
```
G (locked → F free)      -- Si verrouillé, finalement libéré
G (initiated → F done)   -- Si initié, finalement complété
```

### Sûreté des accounts
```
G ¬(p < 0)            -- Jamais de solde négatif
G (transfer → F ack)  -- Virement → confirmation
```

### Équité
```
G (enabled → F executed)   -- Si possible, exécuté
F (a ∧ F b)                -- a puis finalement b
G (p U q)                  -- p jusqu'à q
```

---

## Patterns LTL utiles

| Pattern | Formule | Utilisation |
|---------|---------|-------------|
| "Toujours p" | `G p` | Propriété invariante |
| "Finalement p" | `F p` | Propriété accessibilité |
| "p puis q" | `p U q` | Ordonnancement |
| "Si p alors finalement q" | `G (p → F q)` | Implication temporelle |
| "p et jamais q" | `p ∧ G ¬q` | Conjonction avec négation |
| "Pas p jusqu'à q" | `¬p U q` | Attendre condition |
| "Toujours p ou Q" | `G (p ∨ q)` | Disjunction continue |

---

## Règles de prédominance (parsing)

```
1. Parenthèses: ( )
2. Opérateurs temporels: X, F, G
3. Négation: ¬, !
4. Conjonction: ∧, &
5. Disjonction: ∨, |
6. Until/Release: U, R
```

**Exemples de parsing:**
```
¬p ∧ q      =  (¬p) ∧ q
X F p       =  X (F p)
p U q ∨ r   =  (p U q) ∨ r
G p ∧ q     =  (G p) ∧ q
```

---

## Cas d'usage système bancaire

### 1. Absence de deadlock
```ltl
G (available | locked | processing)
```
Signifie: Un compte est toujours dans l'un des états (disponible, verrouillé, traitement).

### 2. Isolation atomique
```ltl
G (locked → F available)
```
Si un compte est verrouillé, il sera finalement libéré.

### 3. Garantie de virement
```ltl
G (transferInitiated → F (done ∨ failed))
```
Tout virement initié sera complété ou échouera.

### 4. Intégrité de solde
```ltl
G ¬(balance < 0)
```
Le solde ne sera jamais négatif.

### 5. Vivacité distribué
```ltl
G (enabled → F executed)
```
Une opération possible sera exécutée.

---

## Données utiles de débogage

Pour vérifier vos formules LTL:

```scala
import petri._

// Afficher l'AST (Abstract Syntax Tree)
val f = LTLParser.parse("G (p -> F q)")
println(f)  // Globally(Implies(Atom("p"), Finally(Atom("q"))))

// Vérifier syntaxe
try {
  LTLParser.parse("invalid syntax here")
} catch {
  case e: ParseException =>
    println(s"Erreur: ${e.getMessage}")
}

// Tracer évaluation
val result = checker.check("G p")
result.isValid  // true/false
result.message  // Détails
```

---

## Conseils pratiques

### ✅ À faire
```
✓ G p           -- Propriété invariante simple
✓ F p           -- Propriété accessibilité
✓ G (p → F q)   -- Implication temporelle
✓ p U q         -- Ordonnancement strict
✓ G (p ∧ q)     -- Conjonction d'invariants
```

### ❌ À éviter
```
✗ G F p         -- Infinitement souvent p (difficile à vérifier)
✗ F G p         -- Finalement toujours p (propriété très forte)
✗ p U q U r     -- Nested until (ambigu)
✗ Formules très imbriquées (difficiles à déboguer)
```

---

## Références

- **LTL Wikipedia**: https://en.wikipedia.org/wiki/Linear_temporal_logic
- **Model Checking**: https://en.wikipedia.org/wiki/Model_checking
- **Specification Patterns**: http://patterns.projects.cis.ksu.edu/
- **Handbook of Temporal Reasoning**: Cambridge University Press

---

**Référence rapide pour le parseur/évaluateur LTL**
**Système Bancaire Distribué - CY Tech 2026**
