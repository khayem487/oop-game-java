"""Build the console edition with JDK21, without JavaFX or Maven downloads."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
build_root = root / ".build"
build_root.mkdir(exist_ok=True)
sources = sorted((root / "src/main/java/game/model").rglob("*.java"))
sources += sorted((root / "src/main/java/game/cli").rglob("*.java"))
with tempfile.TemporaryDirectory(prefix="console-build-", dir=build_root) as temporary:
    classes = Path(temporary).resolve()
    if not classes.is_relative_to(build_root.resolve()):
        raise SystemExit("Build directory escaped .build.")
    subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)], check=True)
    artifact = build_root / "game-console.jar"
    subprocess.run(["jar", "--create", "--file", str(artifact), "--main-class", "game.cli.Main", "-C", str(classes), "."], check=True)
print("Built", artifact)
print("Run: java -jar .build/game-console.jar --campaign")
