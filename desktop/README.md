# Édition JavaFX

Cette interface de bureau exécute le moteur `game.model` partagé avec les éditions console et web. Elle couvre les objectifs JavaFX des pages 43–48 du sujet original (`src/app.pdf`). Les six cartes de campagne restent distinctes des six mondes pédagogiques.

## Architecture

- **Abstraction :** `GameEngine`, ses règles et ses instantanés immuables. Aucun déplacement, combat, inventaire ou calcul de score n’est recodé dans l’interface.
- **Contrôle :** `GameController` transforme les directions, les clics et les impulsions du minuteur en commandes du moteur.
- **Présentation :** `GamePresentation`, `BoardCanvas` et `MenuPresentation`. `GameState` expose des propriétés JavaFX en lecture seule pour le nom, le score, les vies, l’objectif, les messages et les états du jeu, ainsi qu’une liste observable d’inventaire non modifiable.

`DesktopApplication` gère les scènes, les dialogues et un `AnimationTimer`. Le personnage répète sa dernière direction toutes les 240 ms. Le moteur décide du mouvement des ennemis après les tentatives du joueur. Les dimensions du Canvas suivent son panneau ; les contrôles latéraux restent accessibles par défilement dans une petite fenêtre.

## Commandes

| Commande | Action |
| --- | --- |
| Flèches / ZQSD / WASD | Choisir la direction continue |
| Espace, P ou Échap | Pause / reprise |
| 1–5 ou clic dans l’inventaire | Utiliser l’emplacement correspondant |
| T | Trier l’inventaire |
| N | Passer à la prochaine carte après réussite |
| R | Demander de recommencer de zéro |
| F1 | Afficher l’aide |

Le jeu se met également en pause lorsque sa fenêtre perd le focus. Les dialogues suspendent le mouvement. Revenir au menu déconnecte le contrôleur de l’animation. Les états de victoire et de défaite refusent les déplacements, les objets et la reprise ; les actions de fin de partie restent disponibles.

Le menu propose de commencer, de lire l’aide, d’afficher les crédits ou de quitter. Le dialogue de début valide le nom et la graine avant de créer la campagne. Les confirmations de retour au menu, de nouvelle tentative et de fermeture préservent la partie si elles sont annulées. Les crédits mentionnent l’auteur, le sujet et l’assistance de programmation.

## Construire et lancer depuis les sources

Un JDK 21 et l’accès aux dépendances Maven sont nécessaires. JavaFX est épinglé à la version 21.0.13 dans le profil `desktop`.

```powershell
.\mvnw.cmd -Pdesktop package
.\mvnw.cmd -Pdesktop javafx:run
```

Le paquet Windows fourni séparément inclut un runtime privé ; il ne requiert pas d’installation globale de JavaFX. Les sources JavaFX ne font pas partie du build web par défaut.

## Vérifications de l’interface (23 octobre 2026)

Sept tests JUnit de `GameControllerTest` vérifient notamment la cadence du mouvement, le changement de direction, la pause sans rattrapage de frames, les notifications des propriétés, l’activation d’un consommable, le passage de carte et le report du score, le redémarrage et le blocage des états terminaux.

Deux sondes exécutent réellement JavaFX avec ses bibliothèques natives Windows, créent les composants du jeu et utilisent leurs événements JavaFX. Elles ne simulent pas le moteur et ne pilotent pas les autres applications Windows :

- `DesktopSnapshotProbe` contrôle le clavier, un clic d’inventaire qui consomme un sablier, la mise à jour du score et des vies, la pause/reprise, les actions du menu, le passage de carte, la victoire, la défaite et le redémarrage. Elle vérifie le blocage des commandes dans les états terminaux et l’accès aux derniers contrôles de la barre latérale à 920 × 700.
- `DesktopDialogProbe` ouvre les vrais dialogues de l’application. Elle vérifie le rejet d’un nom vide et d’une graine invalide, le démarrage de la campagne, le mouvement réel, la suspension du jeu derrière l’aide, les crédits, l’annulation et la confirmation du redémarrage et du retour au menu, la déconnexion du jeu masqué et l’annulation de la fermeture. Les actions du menu restent dans la surface disponible à 920 × 700.

Ces sondes produisent 12 captures PNG de composants et de dialogues, examinées visuellement dans `outputs/jeu-javafx-qa-oct23` dans l’espace de travail de livraison. Les captures couvrent la partie, le menu, les petites dimensions, le défilement, la pause, la victoire, la défaite, la validation de saisie, l’aide, les crédits et une confirmation. Le gestionnaire de perte de focus est également contrôlé dans le code ; aucune automatisation globale du clavier/souris du système n’est utilisée.

Pour reproduire les sondes depuis PowerShell :

```powershell
.\mvnw.cmd -Pdesktop '-Dgame.build.directory=.build/desktop-review' '-Dtest=GameControllerTest' package
$desktopLibraries = (Get-ChildItem .build/desktop-review/desktop-dependencies/*-win.jar).FullName -join ';'
& java --module-path $desktopLibraries --add-modules javafx.controls -cp '.build/desktop-review/classes;.build/desktop-review/test-classes' game.desktop.DesktopSnapshotProbe .build/desktop-qa
& java --module-path $desktopLibraries --add-modules javafx.controls -cp '.build/desktop-review/classes;.build/desktop-review/test-classes' game.desktop.DesktopDialogProbe .build/desktop-qa
```

Le bureau ne sauvegarde pas la partie à la fermeture. Le texte de confirmation de sortie l’indique. Les images sont dessinées localement dans le Canvas, sans téléchargement ni appel à un service externe.
