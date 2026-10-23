# Paquet Windows autonome — vérification du 23 octobre 2026

Le livrable `OOP-Game-Windows.zip` contient le jeu compilé, un runtime Java privé et JavaFX. Il suffit d'extraire tout le dossier `OOP-Game`, puis d'ouvrir `Jouer.cmd`. Aucun Java ni JavaFX système n'est installé ou modifié.

## Contenu vérifié

| Élément | Résultat |
| --- | --- |
| Archive | 41 801 609 octets, 170 fichiers, CRC intégral valide |
| SHA-256 | `b4c25a8072607de833d4f52521113803f9cd5d95f5d94f5920c96f716b8f4ab2` |
| Jeu | `oop-game.jar`, avec le moteur, la campagne, les classes JavaFX et la feuille de style |
| Runtime privé | Eclipse Temurin OpenJDK 21.0.12+8, Windows x64, construit par `jlink` |
| JavaFX | Exactement les trois JAR modulaires Windows `base`, `graphics` et `controls`, version 21.0.13 |
| Lanceurs | `Jouer.cmd` et `Diagnostiquer.cmd`, chemins relatifs au script et entourés de guillemets |
| Notices | Notices du runtime sous `runtime/legal` ; licences OpenJFX et composants tiers sous `legal` |

Les modules Java intégrés sont `java.base`, `java.datatransfer`, `java.xml`, `java.prefs`, `java.desktop`, `java.logging`, `jdk.crypto.ec`, `jdk.httpserver` et `jdk.unsupported`. La validation du graphe de modules avec les trois JAR JavaFX réussit.

## Construction et vérifications exécutées

```powershell
python scripts/package-desktop.py --build-directory .build/windows-package
```

Cette commande lance réellement Maven avec le profil `desktop`, exécute les tests et construit le ZIP dans `dist/`. Le build final a exécuté **81 tests JUnit, sans échec, erreur ni test ignoré**. Le répertoire de build séparé évite les conflits avec les autres vérifications Maven.

Le script vérifie les classes nécessaires dans le JAR, la présence des trois modules JavaFX, le JDK 21 du constructeur, l'architecture du runtime produit, la cohérence du graphe de modules et le CRC de l'archive. Les ressources Maven et le runtime privé proviennent des dépendances déjà utilisées par le projet. Pour conserver les notices absentes des JAR Maven, le script récupère une fois le [SDK OpenJFX officiel de Gluon](https://gluonhq.com/products/javafx/), de même version, sous `.build/`. L'archive SDK est épinglée par SHA-256 ; seuls ses textes légaux pour les trois modules distribués sont copiés dans le jeu. Les [sources OpenJFX 21](https://github.com/openjdk/jfx21u) sont référencées dans les notices.

Le ZIP final a été extrait dans un chemin comprenant des espaces et un accent français : `.build/Répertoire de test/OOP-Game`. Les deux sondes ont ensuite été exécutées avec **le `java.exe`, le JAR de jeu et les bibliothèques du dossier extrait**, en ajoutant uniquement les classes des sondes au classpath de vérification :

```powershell
$packageDirectory = Join-Path (Get-Location) '.build/Répertoire de test/OOP-Game'
$privateJava = Join-Path $packageDirectory 'runtime/bin/java.exe'
$privateLibraries = Join-Path $packageDirectory 'lib'
$probeClassPath = (Join-Path $packageDirectory 'oop-game.jar') + ';' +
    (Join-Path (Get-Location) '.build/windows-package/test-classes')
& $privateJava --module-path $privateLibraries --add-modules javafx.controls `
    -cp $probeClassPath game.desktop.DesktopSnapshotProbe .build/windows-runtime-final-qa
& $privateJava --module-path $privateLibraries --add-modules javafx.controls `
    -cp $probeClassPath game.desktop.DesktopDialogProbe .build/windows-runtime-final-qa
```

- `DesktopSnapshotProbe` : **réussite** du clavier, du vrai moteur, de l'inventaire, des propriétés du HUD, de la pause, des actions de menu, de l'accès aux contrôles en petite fenêtre, du passage de carte, de la victoire, de la défaite et du redémarrage ; sept captures PNG.
- `DesktopDialogProbe` : **réussite** des vrais dialogues, de la validation du nom et de la graine, des confirmations annulées et acceptées, de l'aide avec pause, des crédits, de la déconnexion d'un jeu masqué et de l'annulation de la fermeture ; cinq captures PNG.

Ces sondes utilisent les événements JavaFX de l'application et ferment leurs fenêtres. Elles n'automatisent pas les entrées globales de Windows. Les classes de sonde ne sont pas nécessaires à l'utilisateur et ne sont pas ajoutées au ZIP. Les 12 captures produites avec le runtime privé sont conservées dans les livrables sous `outputs/jeu-javafx-runtime-prive-oct23/`. Le menu, la partie et le dialogue d'aide ont aussi été examinés visuellement dans cette exécution ; la revue complète des autres états est décrite dans `desktop/README.md`.

## Limite de chemin observée

Les espaces et accents français fonctionnent sur le PC vérifié. Un chemin contenant le caractère grec `Ω`, non représentable dans son encodage Windows courant, provoque en revanche une erreur du lanceur Temurin avant le démarrage de Java (`could not find java.dll`). Cela ne vient pas des règles du jeu. Le fichier `LIRE-MOI.txt` décrit la limite ; `Jouer.cmd` contrôle le démarrage du runtime et donne une instruction lisible en cas d'échec.

Dans ce cas, extraire le dossier dans un chemin simple, par exemple `C:\Users\khayem\Downloads\OOP-Game`, puis relancer `Jouer.cmd`. Aucun changement de compte, de langue, d'encodage Windows ou de Java système n'a été effectué.

La version bureau ne sauvegarde pas la partie à sa fermeture, comme indiqué dans sa confirmation de sortie. Les contrôles de lancement des scripts ont été inspectés ; la vérification fonctionnelle utilise les commandes du runtime privé ci-dessus, sans prétendre avoir automatisé un double-clic dans l'Explorateur Windows.

## Preuves de livraison

Dans le dossier `outputs` de l'espace de travail :

- `OOP-Game-Windows.zip` : copie finale, empreinte identique à `dist/OOP-Game-Windows.zip` ;
- `oop-game-windows-package-qa-oct23.json` : contrôles, taille et empreinte ;
- `oop-game-windows-build-oct23.log` : build Maven complet ;
- `oop-game-windows-scenes-oct23.log` et `oop-game-windows-dialogs-oct23.log` : sondes du runtime privé ;
- `jeu-javafx-runtime-prive-oct23/` : les 12 captures réelles.
