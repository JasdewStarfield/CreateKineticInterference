"""Verify old config detection and saved model selection across real server restarts."""

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME = ROOT / "run" / "validation-model-migration"
CONFIG = GAME / "config" / "createkineticinterference-server.toml"


def main():
    # Reuse this script's world across stages so SavedData, rather than an in-memory cache, is tested.
    if (GAME / "world").exists():
        raise RuntimeError("Migration fixture already exists; choose a fresh directory in this script before rerunning")
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    CONFIG.write_text("[general.waterwheel]\ninterferenceRadius = 32.0\ninterferenceFactor = 0.1\n", encoding="utf-8", newline="\n")
    for label, requested, expected in (
        ("old-config", None, "LEGACY"),
        ("saved-auto", "AUTO", "LEGACY"),
        ("explicit-density", "DENSITY", "DENSITY"),
        ("explicit-legacy", "LEGACY", "LEGACY"),
        ("returned-auto", "AUTO", "LEGACY"),
    ):
        if requested:
            content = CONFIG.read_text(encoding="utf-8")
            line = f'calculationModel = "{requested}"'
            content = re.sub(r"(?m)^calculationModel\s*=.*$", line, content)
            CONFIG.write_text(content, encoding="utf-8", newline="\n")
        args = [str(ROOT / "gradlew.bat"), "runGameTestServer", f"-PgameTestDir={GAME}",
                "--no-configuration-cache", "--no-daemon", "--console=plain"]
        if expected == "DENSITY":
            args.append("-PdensityTests")
        log = ROOT / "build" / f"migration-{label}.log"
        with log.open("w", encoding="utf-8") as output:
            result = subprocess.run(args, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        content = log.read_text(encoding="utf-8", errors="replace")
        count = 5 if expected == "DENSITY" else 4
        if result.returncode or f"All {count} required tests passed" not in content or f"CKI calculation model for this world: {expected}" not in content:
            raise RuntimeError(f"Migration stage {label} failed: {log}")
        markers = list(GAME.rglob("cki_calculation_model.dat"))
        if len(markers) != 1:
            raise RuntimeError(f"Expected one global saved model marker, found {markers}")
        print(f"{label}: {expected}; {count}/{count} passed; marker={markers[0]}", flush=True)


if __name__ == "__main__":
    main()
