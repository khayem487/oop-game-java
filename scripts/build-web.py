"""Build the actual shared Java model and HTTP edition with JDK21, no libraries."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
build_root = root / ".build"
build_root.mkdir(exist_ok=True)
sources = sorted((root / "src/main/java/game/model").rglob("*.java"))
sources += sorted((root / "web/src").rglob("*.java"))
if not sources:
    raise SystemExit("No model/web Java source files found.")
with tempfile.TemporaryDirectory(prefix="web-build-", dir=build_root) as temporary:
    classes = Path(temporary).resolve()
    if not classes.is_relative_to(build_root.resolve()):
        raise SystemExit("Build directory escaped .build.")
    subprocess.run(["javac", "--release", "21", "--add-modules", "jdk.httpserver", "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)], check=True)
    shutil.copytree(root / "web/public", classes / "public")
    artifact = build_root / "game.jar"
    subprocess.run(["jar", "--create", "--file", str(artifact), "--main-class", "game.web.GameServer", "-C", str(classes), "."], check=True)
print("Built", artifact)
print("Run: java --add-modules jdk.httpserver -jar .build/game.jar 8080")
