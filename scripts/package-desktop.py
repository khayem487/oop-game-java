"""Create a Windows game ZIP including a private Java21 runtime, with no install."""
from pathlib import Path
import argparse
import hashlib
import os
import platform
import re
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile


def copy_javafx_notices(package, temporary_path, build_root, modules):
    """Preserve the upstream notices for the exact three redistributed FX modules."""
    version = "21.0.13"
    expected_names = {f"javafx-{name}-{version}-win.jar" for name in ("base", "graphics", "controls")}
    if {module.name for module in modules} != expected_names:
        raise SystemExit("Update the pinned JavaFX legal bundle when changing the JavaFX version.")
    sdk_url = f"https://download2.gluonhq.com/openjfx/{version}/openjfx-{version}_windows-x64_bin-sdk.zip"
    sdk_hash = "21e021e9d599ce50cdaa3b7d4db4492dabf030badffb57d62dfe186ec1b3d81f"
    sdk = build_root / f"openjfx-{version}-windows-sdk.zip"
    if not sdk.is_file():
        downloaded = temporary_path / "javafx-notices-sdk.zip"
        print("Downloading official OpenJFX SDK once to preserve its license notices...", flush=True)
        with urllib.request.urlopen(sdk_url, timeout=45) as response, downloaded.open("wb") as destination:
            shutil.copyfileobj(response, destination)
        with downloaded.open("rb") as source:
            if hashlib.file_digest(source, "sha256").hexdigest() != sdk_hash:
                raise SystemExit("The downloaded OpenJFX SDK differs from the pinned archive.")
        os.replace(downloaded, sdk)
    with sdk.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != sdk_hash:
            raise SystemExit("The cached OpenJFX SDK differs from the pinned archive.")
    legal = package / "legal"
    legal.mkdir()
    with zipfile.ZipFile(sdk) as upstream:
        for module in ("javafx.base", "javafx.graphics", "javafx.controls"):
            prefix = f"javafx-sdk-{version}/legal/{module}/"
            for entry in upstream.infolist():
                if entry.is_dir() or not entry.filename.startswith(prefix):
                    continue
                filename = entry.filename[len(prefix):]
                if "/" in filename or "\\" in filename or filename in {".", ".."} or entry.file_size > 131_072:
                    raise SystemExit("Unexpected JavaFX legal notice path or size.")
                target = legal / module / filename
                target.parent.mkdir(exist_ok=True)
                target.write_bytes(upstream.read(entry))
            if not (legal / module / "LICENSE").is_file():
                raise SystemExit("Missing license for " + module)
    (legal / "THIRD-PARTY-NOTICES.txt").write_text(
        "This package includes OpenJFX 21.0.13 base/graphics/controls from org.openjfx Maven artifacts.\n"
        "The corresponding unmodified license and third-party notices are preserved in this folder.\n"
        "Official SDK notice source: " + sdk_url + "\n"
        "OpenJFX 21 source: https://github.com/openjdk/jfx21u\n"
        "The private Eclipse Temurin Java runtime keeps its own notices under runtime/legal.\n"
        "Temurin 21 source: https://github.com/adoptium/jdk21u\n", encoding="utf-8")

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--skip-build", action="store_true", help="Use an already verified desktop Maven package")
parser.add_argument("--build-directory", type=Path, default=Path("target"),
                    help="Maven build directory, relative to the repository unless absolute")
parser.add_argument("--output", type=Path, default=root / "dist/OOP-Game-Windows.zip")
args = parser.parse_args()
if platform.system() != "Windows":
    raise SystemExit("This distribution bundles Windows JavaFX native libraries; build it on Windows.")
if platform.machine().lower() not in {"amd64", "x86_64"}:
    raise SystemExit("This distribution requires a Windows x64 build host and JDK.")
build_directory = args.build_directory
if not build_directory.is_absolute():
    build_directory = root / build_directory
build_directory = build_directory.resolve()
if not args.skip_build:
    subprocess.run([str(root / "mvnw.cmd"), "-B", "-Pdesktop",
                    "-Dgame.build.directory=" + str(build_directory), "package"], cwd=root, check=True)

artifact = build_directory / "oop-game-2.0.0.jar"
dependencies = build_directory / "desktop-dependencies"
if not artifact.is_file() or not dependencies.is_dir():
    raise SystemExit("Run mvnw.cmd -Pdesktop package first.")
with zipfile.ZipFile(artifact) as jar:
    required_classes = {"game/model/GameEngine.class", "game/model/Campaign.class",
                        "game/desktop/DesktopLauncher.class", "game/desktop/game.css"}
    missing_classes = required_classes.difference(jar.namelist())
    if missing_classes:
        raise SystemExit("The game JAR is missing desktop components: " + repr(sorted(missing_classes)))
modules = []
for dependency in dependencies.glob("*.jar"):
    with zipfile.ZipFile(dependency) as jar:
        if "module-info.class" in jar.namelist():
            modules.append(dependency)
if len(modules) != 3 or not all("-win.jar" in module.name for module in modules):
    raise SystemExit("Expected the three modular Windows JavaFX base/graphics/controls JARs, got: " + repr([module.name for module in modules]))
if {module.name.split("-")[1] for module in modules} != {"base", "graphics", "controls"}:
    raise SystemExit("The JavaFX module set must be exactly base, graphics and controls.")
compiler = shutil.which("javac")
if not compiler:
    raise SystemExit("JDK21 with javac/jlink is required to build the bundled runtime.")
compiler_version = subprocess.run([compiler, "-version"], capture_output=True, text=True, check=True)
if not re.search(r"\bjavac 21(?:\.|\s|$)", compiler_version.stdout + compiler_version.stderr):
    raise SystemExit("Build this Java21 distribution with JDK21, got: " + compiler_version.stdout + compiler_version.stderr)
java_home = Path(compiler).resolve().parent.parent
jlink = java_home / "bin/jlink.exe"
if not jlink.is_file():
    raise SystemExit("jlink was not found next to javac.")
build_root = root / ".build"
build_root.mkdir(exist_ok=True)
output = args.output.resolve()
output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix="desktop-package-", dir=build_root) as temporary:
    temporary_path = Path(temporary).resolve()
    if not temporary_path.is_relative_to(build_root.resolve()):
        raise SystemExit("Temporary package escaped .build.")
    package = temporary_path / "OOP-Game"
    libraries = package / "lib"
    libraries.mkdir(parents=True)
    shutil.copy2(artifact, package / "oop-game.jar")
    for module in modules:
        shutil.copy2(module, libraries / module.name)
    copy_javafx_notices(package, temporary_path, build_root, modules)
    subprocess.run([str(jlink), "--module-path", str(java_home / "jmods"),
        "--add-modules", "java.base,java.desktop,java.logging,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.httpserver",
        "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-6",
        "--output", str(package / "runtime")], check=True)
    runtime_java = str(package / "runtime/bin/java.exe")
    runtime_properties = subprocess.run([runtime_java, "-XshowSettings:properties", "-version"],
                                        capture_output=True, text=True, check=True)
    if not re.search(r"\bos.arch\s*=\s*(?:amd64|x86_64)\b", runtime_properties.stderr):
        raise SystemExit("The bundled Java runtime is not Windows x64.")
    subprocess.run([runtime_java, "--module-path", str(libraries),
                    "--add-modules", "javafx.controls", "--validate-modules"], check=True)
    launch = ('@echo off\r\ncd /d "%~dp0"\r\n'
        '"%~dp0runtime\\bin\\java.exe" --version >nul 2>&1\r\n'
        'if errorlevel 1 (\r\n'
        '  echo Le runtime Java ne demarre pas. Extraire le dossier OOP-Game entier.\r\n'
        '  echo Si le chemin contient des caracteres speciaux, deplacer le dossier dans Downloads.\r\n'
        '  echo Utiliser Diagnostiquer.cmd pour afficher le message complet.\r\n'
        '  pause\r\n  exit /b 1\r\n)\r\n'
        'start "" "%~dp0runtime\\bin\\javaw.exe" --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0oop-game.jar" game.desktop.DesktopLauncher\r\n')
    (package / "Jouer.cmd").write_text(launch, encoding="ascii", newline="")
    diagnose = '@echo off\r\ncd /d "%~dp0"\r\n"%~dp0runtime\\bin\\java.exe" --module-path "%~dp0lib" --add-modules javafx.controls -cp "%~dp0oop-game.jar" game.desktop.DesktopLauncher\r\npause\r\n'
    (package / "Diagnostiquer.cmd").write_text(diagnose, encoding="ascii", newline="")
    (package / "LIRE-MOI.txt").write_text(
        "LES JARDINS OUBLIES - JEU JAVA / JAVAFX\n\n"
        "1. Extraire tout le dossier OOP-Game de cette archive.\n"
        "2. Double-cliquer sur Jouer.cmd. Aucun Java a installer.\n"
        "3. Saisir un nom de personnage et demarrer la campagne.\n\n"
        "Commandes : fleches ou ZQSD/WASD pour la direction, inventaire cliquable, pause dans l'interface.\n"
        "Le personnage avance en continu. Lire l'objectif de chaque carte et l'aide des objets.\n"
        "Six cartes, ennemis, portes, pieges, inventaire et competences partagent le meme moteur Java\n"
        "que les editions console et navigateur. Les cartes sont distinctes des mondes du cours.\n\n"
        "En cas de probleme de lancement : utiliser Diagnostiquer.cmd pour voir le message.\n"
        "Les chemins avec espaces et accents francais ont ete testes. Certains caracteres\n"
        "hors de l'encodage Windows courant peuvent bloquer Java : dans ce cas, deplacer le dossier\n"
        "dans un chemin simple, par exemple C:\\Users\\khayem\\Downloads\\OOP-Game.\n"
        "Ce dossier est autonome pour Windows x64 et ne modifie pas votre Java systeme.\n"
        "Les notices des composants tiers sont conservees sous legal et runtime/legal.\n"
        "Sources : https://github.com/khayem487/oop-game-java\n",
        encoding="utf-8")
    staged_zip = temporary_path / "OOP-Game-Windows.zip"
    with zipfile.ZipFile(staged_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(package.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(temporary_path))
    with zipfile.ZipFile(staged_zip) as archive:
        broken = archive.testzip()
        if broken:
            raise SystemExit("ZIP verification failed: " + broken)
    os.replace(staged_zip, output)
print("Built", output)
print("Includes a private Windows Java21 runtime and JavaFX; no system installation needed.")
