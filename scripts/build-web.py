"""Build the web adapter with only JDK 21+, no Maven or downloaded libraries."""
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
classes = root / ".build" / "classes"
classes.mkdir(parents=True, exist_ok=True)
sources = [root / "src" / name for name in ("Player.java", "Level.java")]
sources += sorted((root / "web" / "src").glob("*.java"))
subprocess.run(["javac", "--release", "21", "--add-modules", "jdk.httpserver", "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)], check=True)
shutil.copytree(root / "web" / "public", classes / "public", dirs_exist_ok=True)
artifact = root / ".build" / "game.jar"
subprocess.run(["jar", "--create", "--file", str(artifact), "--main-class", "GameServer", "-C", str(classes), "."], check=True)
print("Built", artifact)
print("Run: java --add-modules jdk.httpserver -jar .build/game.jar 8080")
