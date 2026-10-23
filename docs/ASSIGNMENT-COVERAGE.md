# Correspondance avec le sujet de POO

Vérification technique du **23 octobre 2026**. Ce document relie les objectifs du support de cours à la version 2 du jeu et à ses preuves reproductibles. Il décrit l’implémentation ; il n’accorde aucune validation pédagogique à l’étudiant.

## Source et périmètre

Le support **Introduction à la programmation objet**, 50 pages, est conservé dans l’historique du dépôt : [PDF original au commit `8e46b695…`](https://github.com/khayem487/oop-game-java/blob/8e46b6950f3b7c7218e405187ff257d21513c2fa/src/app.pdf).

Empreinte SHA-256 de la copie locale contrôlée :

```text
48ce83cffd8b83f122040b7a98d00ff1bb63e0e67d4bbf920d4c437fd1d852be
```

Les pages citées ci-dessous sont les numéros des pages du PDF, également imprimés sur les diapositives. Les pages 7, 14, 21, 28, 35 et 42 présentent les plans des mondes ; leurs objectifs détaillés commencent à la page suivante. Le support répète « Niveau 2 » à la page 38 : cette page correspond au sablier.

La page 2 mentionne des devoirs Teams et une grille Excel de validation. Cette grille n’a pas été retrouvée et les éventuelles consignes supplémentaires sur Teams ne sont pas incluses dans cette vérification. La version a été complétée avec une assistance de programmation ; les tests démontrent des comportements du logiciel, pas la maîtrise personnelle de chaque notion.

## Monde 1 · Joueur, encapsulation et contrats — pages 8–13

| Pages | Objectif | Implémentation finale | Preuves |
| --- | --- | --- | --- |
| 8–9 | Joueur nommé, score initial nul, nom immuable et score non négatif | [`Player`](../src/main/java/game/model/Player.java), héritant de [`Actor`](../src/main/java/game/model/Actor.java). Score protégé contre les valeurs négatives et les débordements. | `PlayerTest.scoreNeverUnderflowsOrOverflows`, tests de validation des noms. |
| 10 | Documentation et génération HTML | Javadoc des classes publiques et plugin Maven configuré. | `mvnw.cmd javadoc:javadoc` réussit ; sortie `target/reports/apidocs/index.html`. |
| 11 | Représentation textuelle, accord de `pt` / `pts` | `Player.toString()`. | `PlayerTest.scoreTextAgreesAtZeroOneAndSeveralPoints`. |
| 12 | Égalité par nom sans tenir compte de la casse | `equals` et `hashCode` cohérents, y compris pour les cas Unicode testés. | `PlayerTest.equalityUsesOnlyCaseInsensitiveNameIncludingUnicode`. |
| 13 | Compteur global et constructeur `JoueurN` | Compteur statique et constructeurs nommés / par défaut. | `PlayerTest.generatedNamesCountNamedAndUnnamedPlayers`. |

Les démonstrations ponctuelles « Alice » / « Bob » du cours sont représentées par les tests ; le programme final démarre la vraie campagne au lieu d’imprimer toutes les démonstrations intermédiaires.

## Monde 2 · Niveau et console — pages 15–20

| Pages | Objectif | Implémentation finale | Preuves |
| --- | --- | --- | --- |
| 15–16 | Grille, composition avec le joueur, placement et erreurs explicites | [`LevelDefinition`](../src/main/java/game/model/LevelDefinition.java) valide une grille rectangulaire avec exactement un joueur ; [`Level`](../src/main/java/game/model/Level.java) instancie les cellules et acteurs. | `LevelValidationTest.rejectsMalformedOrUnusableMaps`, validation des dimensions, symboles, joueur absent / multiple et objectifs incohérents. |
| 17 | Quatre directions et collision avec les murs | `Direction`, `Level.neighbour`, méthodes polymorphes `Cell.canEnter`. | `GameRulesTest.cellsProvidePolymorphicCollisionRulesAndDoorsStayClosedToEnemies`, `toroidalNeighboursWrapInEveryDirectionAndBoundedOnesStop`. |
| 18 | Entrées ZQSD en boucle | [`game.cli.Main`](../src/main/java/game/cli/Main.java), commandes inconnues et vides gérées, aide et sortie propre. | `ConsoleTest.emptyAndUnknownCommandsCanBeFollowedByAProperQuit`. |
| 19 | Lecture de niveaux texte et erreur de fichier | `LevelDefinition.fromFile`, UTF-8, BOM et en-têtes facultatifs ; erreurs d’E/S renvoyées puis expliquées par la console. | `LevelValidationTest.readsUtf8BomHeadersAndPreservesMeaningfulSpaces`, `missingAndOversizedFilesHaveCheckedIoFailures`, `ConsoleTest.missingFileProducesHelpfulFailure`. |
| 20 | JAR exécutable et chemins en arguments | Maven crée un JAR dont le point d’entrée est `game.cli.Main` ; le script JDK produit aussi un JAR console autonome. | `ConsoleTest.noArgumentPrintsUsageAndDoesNotStartAnInvisibleCampaign`, `realFilesChainWhileKeepingPlayerScoreAndName` ; scripts et commandes du README. |

La représentation finale évolue vers une grille de cellules objets, comme demandé au monde 3. Les étapes anciennes en grille de caractères restent dans les archives du dépôt et ne sont pas compilées par Maven.

## Monde 3 · Règles et cellules — pages 22–27

| Pages | Objectif | Implémentation finale | Preuves |
| --- | --- | --- | --- |
| 22 | Pièces à +10 et fin du niveau | Pièces portées par les cellules, retirées au ramassage ; objectif `COLLECT_COINS`. | `GameRulesTest.coinsDisappearIncreaseScoreAndCompleteEachLevelWithoutResettingPlayerData`. |
| 23 | Cinq vies, pièges à −2, destruction, retour au départ, défaite et reprise | `TrapCell`, `GameEngine.loseLives`, consommation du piège et `restart`. La console demande de rejouer après défaite. | `trapsAreConsumedAndThreeTrapHitsCauseGameOverThenCleanRestart`, `losingLivesOnATrapAlsoResetsAllEnemiesWithoutMovingThemAgain`. |
| 24 | Plusieurs fichiers, saisie du nom, conservation des données et victoire finale | Console multi-fichiers, nom demandé à défaut d’option, progression `nextLevel` conservant nom, score, vies et inventaire. | `ConsoleTest.realFilesChainWhileKeepingPlayerScoreAndName`, `CampaignTest` et tests de progression du modèle / contrôleur. |
| 25 | Cellules objets positionnées | Hiérarchie [`Cell`](../src/main/java/game/model/Cell.java) : sol, mur, piège et porte ; chaque objet possède sa `Position`. | Tests de collision polymorphe et snapshots défensifs ; le moteur construit réellement une grille `Cell[][]`. |
| 26 | Porte verrouillée `D`, extension par polymorphisme | [`LockedDoorCell`](../src/main/java/game/model/LockedDoorCell.java) définit sa traversabilité. Le moteur n’ajoute pas un cas de collision par type de terrain. | `cellsProvidePolymorphicCollisionRulesAndDoorsStayClosedToEnemies`, test de frontière du crochetage et porte effectivement franchie dans `CampaignTest`. |
| 27 | Topologie torique | Voisins calculés par modulo pour une carte torique ; la campagne possède aussi des cartes aux bords fermés. | `toroidalNeighboursWrapInEveryDirectionAndBoundedOnesStop`, poursuite du chasseur passant par un bord relié. |

## Monde 4 · Ennemis et polymorphisme — pages 29–34

| Pages | Objectif | Implémentation finale | Preuves |
| --- | --- | --- | --- |
| 29 | Ennemi aléatoire à une vie, nom unique, tour après chaque tentative, hiérarchie abstraite | [`Actor`](../src/main/java/game/model/Actor.java), [`Enemy`](../src/main/java/game/model/Enemy.java) et [`RandomEnemy`](../src/main/java/game/model/RandomEnemy.java). Une tentative contre un mur consomme aussi un tour. | `wallAttemptsStillAdvanceTheTurnAndMoveEnemies`, `randomEnemyCannotCrossWallsDoorsOrTrapsAndDoesNotCollectCoins`. |
| 30 | Contacts à −1, noms des attaquants, retour de tous aux positions initiales | Résolution des contacts avant et après le mouvement ennemi ; cumul des attaquants et reset de tous les survivants en cas de perte de vie. | `ordinaryEnemyCollisionLosesOneLifeAndResetsBothActors`, `simultaneousGhostContactsApplyEachDamageAndResetEverySurvivor`, test de reset après piège. |
| 31 | Ensemble des cellules occupées | `Level.getOccupiedEnemyCells()` produit un ensemble défensif de `Position`, qui déduplique les superpositions. | `occupiedCellSetDeduplicatesOverlappingEnemiesAndIsDefensive`. |
| 32 | Fantôme traversant les obstacles et conservant sa direction hors alignement | [`GhostEnemy`](../src/main/java/game/model/GhostEnemy.java), changement de direction si le joueur est sur la même ligne ou colonne. | `ghostCrossesObstaclesKeepsHeadingAndTurnsOnlyWhenAligned`, obstacles mur / porte / piège effectivement traversés. |
| 33 | Chasseur à trois vies, contact à −2, vraie poursuite | [`HunterEnemy`](../src/main/java/game/model/HunterEnemy.java) calcule un plus court chemin par BFS sur le graphe des cellules accessibles. | `hunterFindsActualShortestDetourAndUsesToroidalEdges`, `hunterContactCostsTwoLivesButAutomaticWeaponsPreventDamageAndConsumeOnePerHit`. |
| 34 | Organisation en packages | Sources actives `game.model`, `game.cli`, `game.desktop`, `game.web` ; compilation limitée à ces sources. | Compilation Maven et builds JDK ; les classes historiques hors packages sont archivées hors du build actif. |

## Monde 5 · Inventaire et objectifs — pages 36–41

| Pages | Objectif | Implémentation finale | Preuves |
| --- | --- | --- | --- |
| 36 | Cinq places, typage sûr, items consommables et compétences réutilisables, refus si plein | Interface [`InventoryElement`](../src/main/java/game/model/InventoryElement.java), classes `Item` / `Skill`, collection bornée du joueur. L’objet non ramassé reste au sol et un gain de compétence attend une place libre. | `PlayerTest.inventoryIsTypedBoundedDefensiveAndSortedByTypeThenName`, `GameRulesTest.fullInventoryLeavesItemsOnGroundAndDefersSkillsUntilASlotIsFreed`. |
| 37 | Arme `W` automatique, un dégât à l’ennemi et aucun au joueur, puis consommation | Résolution de collision par le moteur ; une arme par contact protégé. Le chasseur nécessite trois dégâts. | `hunterContactCostsTwoLivesButAutomaticWeaponsPreventDamageAndConsumeOnePerHit`, armes effectivement collectées et consommées pendant `CampaignTest`. |
| 38 | Sablier `H`, dix déplacements d’immobilisation, activation avant un déplacement | `useInventory` ne consomme aucun tour ; le compteur se décrémente à chaque tentative de mouvement, y compris contre un mur. | `hourglassFreezesExactlyTenMovementAttemptsButActivationIsNotATurn` ; les replays de campagne vérifient aussi dix mouvements et les positions des survivants figées. |
| 39 | Crochetage automatique à score strictement supérieur à 100, portes fermées aux ennemis | `learnSkills` et `LockedDoorCell` distinguent le joueur équipé des ennemis. | `lockpickRequiresStrictlyMoreThanOneHundredPointsAndRemainsReusable` teste les seuils 100 / 110 ; refus de la porte aux ennemis testé séparément. |
| 40 | Téléportation après trois défaites ennemies, case vide aléatoire, compétence réutilisable | Destinations de sol vide excluant le joueur et les cellules ennemies ; choix par RNG déterministe. | `threeEnemyDefeatsUnlockReusableTeleportOntoAnUnoccupiedEmptyCell`, `teleportSelectionIsSeededAndReplaysIdentically`, `teleportWithoutAnEmptyDestinationDoesNotMoveOrConsumeTheSkill`. |
| 41 | Tri items puis compétences, ordre des noms ; victoires pièces / ennemis / objet spécial | Comparateur typé et trois stratégies d’objectif dans `LevelDefinition.Goal`. Le trophée est consommé immédiatement pour son objectif. | Test du tri de `PlayerTest`, `questTrophyIsConsumedImmediatelyEvenWithFiveFullInventorySlots`, trois objectifs et six cartes réellement terminés dans `CampaignTest`. |

Le trophée `T` est un objet de quête consommé au ramassage : il valide son objectif sans occuper une sixième place. Ce choix explicite évite un blocage si l’inventaire contient déjà cinq éléments. L’arme et le crochetage sont automatiques ; leur activation manuelle renvoie une explication sans les consommer.

## Monde 6 · Interface JavaFX — pages 43–48

| Pages | Objectif | Implémentation finale | Preuves |
| --- | --- | --- | --- |
| 43 | Application graphique JavaFX | [`DesktopApplication`](../desktop/src/main/java/game/desktop/DesktopApplication.java) ouvre une vraie fenêtre, un menu et une campagne. | Compilation avec le profil `desktop`, lancement et sondes de scènes JavaFX. La version finale remplace l’étape intermédiaire « Hello World ». |
| 44 | Canvas et architecture PAC | [`BoardCanvas`](../desktop/src/main/java/game/desktop/BoardCanvas.java) dessine le niveau ; présentation et contrôle séparés autour du même modèle. | `DesktopSnapshotProbe` construit de vraies scènes et exporte leurs rendus ; `GameControllerTest` exerce le contrôle. |
| 45 | Clavier et mouvement des ennemis | `GamePresentation.installKeyboard` transmet les directions ; le contrôleur appelle le moteur qui déplace les ennemis. | Tests du contrôle et sondes de véritables événements clavier JavaFX. |
| 46 | Nom, score, vies, inventaire et CSS | HUD et cinq emplacements ; styles dans `game.css`. | `GameControllerTest.hudPropertiesTrackTheEngineRatherThanASeparateScore`, vérifications de labels et d’inventaire dans `DesktopSnapshotProbe`. |
| 47 | Mise à jour via propriétés et inventaire cliquable | [`GameState`](../desktop/src/main/java/game/desktop/GameState.java) expose des `ReadOnlyProperty` et une liste observable ; les boutons déclenchent `useInventory`. | `clickingConsumableDoesNotSpendAMovementTurnAndUpdatesObservableInventory`, activation réelle du sablier depuis un bouton dans la sonde graphique. |
| 48 | Menu, crédits, dialogues nom / défaite, mouvement continu par `AnimationTimer` | `DesktopApplication`, `MenuPresentation` et contrôleur cadencé à 240 ms ; pause, perte de focus, confirmation de reprise / sortie et écrans de fin. | Tests de cadence, pause, reprise et états terminaux ; `DesktopDialogProbe` exerce les dialogues réels, validations du nom / de la graine et confirmations ; `DesktopSnapshotProbe` vérifie victoire / défaite et actions de menus. |

Les tests JUnit du contrôleur s’exécutent sans ouvrir de fenêtre. Les deux sondes de scènes nécessitent une session graphique : elles constituent une vérification supplémentaire et ne sont pas comptées parmi les 81 exécutions JUnit. Une vérification graphique ne remplace pas des essais sur toutes les tailles de fenêtre, configurations d’affichage et plateformes. Le ZIP livré par le script cible Windows x64.

La version bureau est conçue pour les mécanismes JavaFX demandés, y compris le déplacement continu après choix de direction. La version web conserve les règles mais propose une interaction adaptée au navigateur : maintien d’une direction, commandes tactiles et sauvegarde locale.

## Monde débogage — page 50

Les tests, les traces de compilation et `Diagnostiquer.cmd` rendent les erreurs reproductibles. Les sources peuvent être ouvertes dans un IDE pour poser des points d’arrêt et observer les variables. Ces possibilités ne prouvent pas que chaque exercice de débogage a été réalisé personnellement : la maîtrise du débogueur et sa validation restent à vérifier avec le cours et l’enseignant.

## Preuves d’exécution reproductibles

### Tests JUnit

Commande :

```powershell
.\mvnw.cmd -Pdesktop test
```

Dernière vérification locale du 23 octobre 2026, avec un dossier de build isolé : **81 exécutions réussies, 0 échec, 0 erreur, 0 ignoré**.

| Suite réelle | Exécutions | Ce qu’elle vérifie |
| --- | --- | --- |
| [`PlayerTest`](../src/test/java/game/model/PlayerTest.java) | 11 | Score, nom, Unicode, compteur, représentation et inventaire. |
| [`LevelValidationTest`](../src/test/java/game/model/LevelValidationTest.java) | 24 | Cartes, dimensions, symboles, objectifs, fichiers et en-têtes. |
| [`GameRulesTest`](../src/test/java/game/model/GameRulesTest.java) | 23 | Déplacements, collisions, tore, ennemis, objets, compétences et déterminisme. |
| [`CampaignTest`](../src/test/java/game/model/CampaignTest.java) | 4 | Deux victoires complètes, diversité des cartes, restart et isolation. |
| [`ConsoleTest`](../src/test/java/game/cli/ConsoleTest.java) | 4 | Arguments, fichiers, enchaînement réel, erreurs et sortie. |
| [`GameServerTest`](../web/tests/GameServerTest.java) | 8 | Vrai serveur HTTP local, commandes, isolation, entrées rejetées, origines et ressources. |
| [`GameControllerTest`](../desktop/src/test/java/game/desktop/GameControllerTest.java) | 7 | Cadence JavaFX, pause, HUD observable, inventaire, progression et fins de partie. |

Les exécutions paramétrées sont comptées individuellement par JUnit. Les tests emploient des cartes ciblées et des comportements attendus ; le total ne correspond pas à un nombre d’exigences validées par un enseignant.

### Deux victoires de campagne sans triche

Les solutions de [`campaign-solution.json`](../scripts/campaign-solution.json) utilisent uniquement `move`, `useInventory` et `nextLevel`, avec le nom `Explorateur`. Aucun accès interne ne fixe le score, les vies, la position ou l’état de victoire.

| Graine | Commandes, transitions et inventaire inclus | Résultat |
| --- | --- | --- |
| `20261022` | 90 | Six cartes, 180 points, cinq vies restantes et six ennemis vaincus. |
| `1337` | 91 | Six cartes, 180 points, cinq vies restantes et six ennemis vaincus. |

Chaque carte possède un préfixe d’historique et un checkpoint. Les tests vérifient notamment les scores **60 → 130 → 150 → 180 → 180 → 180**, les objectifs de chaque carte, le passage réel de la porte, le sablier, les armes et les compétences obtenues. Ces deux exemples ne démontrent pas une solution pour toute graine imaginable ; ils établissent deux parcours complets indépendants avec les règles réelles.

### API et interface graphique

```powershell
python scripts/build-web.py
python scripts/test-web.py
python scripts/test-web.py https://khayem-oop-game.vercel.app
```

Le script HTTP v2 consomme les historiques de la campagne. Sans URL, il démarre son propre processus Java local puis l’arrête ; avec une URL, il teste ce déploiement. Son résultat concerne l’API réellement interrogée et doit être distingué du résultat des seuls tests unitaires et de la revue visuelle du navigateur.

Les sondes graphiques sont disponibles dans [`DesktopSnapshotProbe`](../desktop/src/test/java/game/desktop/DesktopSnapshotProbe.java) et [`DesktopDialogProbe`](../desktop/src/test/java/game/desktop/DesktopDialogProbe.java). Elles ouvrent le toolkit JavaFX et actionnent les véritables composants, avec export de captures. Elles ne constituent pas une simulation HTML de l’interface bureau.

## Limites et choix explicites

- **Pas de validation de l’enseignant ni de grille Excel disponible.** Les étapes de dépôt Teams et les éventuelles exigences annexes ne sont pas attestées ici.
- **Six cartes de campagne, pas six mondes pédagogiques homologués.** Les cartes combinent les mécanismes et objectifs des six mondes. Les anciens exercices et la première démo restent archivés pour l’historique.
- **Adaptation web supplémentaire.** Le serveur Java fait autorité sur les règles ; l’interface navigateur n’est pas JavaFX et ne remplace pas l’édition bureau demandée par le monde 6.
- **Sauvegarde du navigateur uniquement.** Le nom, la graine et l’historique restent dans son stockage local. Console et JavaFX ne restaurent pas une partie après fermeture ; ce n’est pas une exigence identifiée dans le PDF.
- **API publique bornée.** Historiques limités à 2 048 actions, corps à 4 096 octets et budget de rejeu. Ce modèle convient à une campagne solo courte, sans classement compétitif ni garantie contre l’automatisation de parties.
- **Fichiers personnalisés par la console.** Le serveur public ne lit pas un chemin ou une URL de niveau arbitraire fourni par un visiteur.
- **Hébergement distinct de la conformité pédagogique.** Ce dépôt utilise une publication Vercel manuelle. Les autorisations Git automatiques d’autres dépôts ne s’appliquent pas à celui-ci ; un push GitHub seul ne publie pas le jeu. Les quotas et démarrages à froid dépendent du service.
