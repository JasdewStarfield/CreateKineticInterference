"""Run isolated LEGACY/DENSITY compatibility scenarios and require explicit GameTest verdicts."""

import argparse
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ("baseline", "picky", "flowing", "both")


def run_scenario(model, scenario, tag):
    # This script owns only validation directories under run/ and build/; player worlds stay separate.
    suffix = f"-{tag}" if tag else ""
    game_dir = ROOT / "run" / f"validation-{model.lower()}-{scenario}{suffix}"
    config = game_dir / "config" / "createkineticinterference-server.toml"
    config.parent.mkdir(parents=True, exist_ok=True)
    text = config.read_text(encoding="utf-8") if config.exists() else ""
    line = f'calculationModel = "{model}"'
    if re.search(r"(?m)^calculationModel\s*=", text):
        text = re.sub(r"(?m)^calculationModel\s*=.*$", line, text)
    else:
        text = line + "\n" + text
    config.write_text(text, encoding="utf-8", newline="\n")
    args = [str(ROOT / "gradlew.bat"), "runGameTestServer", f"-PgameTestDir={game_dir}",
            "--no-configuration-cache", "--no-daemon", "--console=plain"]
    if model == "DENSITY":
        args.append("-PdensityTests")
    if scenario != "baseline":
        mods = ROOT / "build" / "compat-mods" / scenario
        manifest = json.loads((ROOT / "build/compat-mods/manifest.json").read_text(encoding="utf-8"))
        expected = {entry["filename"] for entry in manifest
                    if scenario == "both" or (scenario == "picky") == entry["filename"].startswith("createpickywheels")}
        actual = {path.name for path in mods.glob("*.jar")}
        if expected != actual:
            raise RuntimeError(f"Unexpected compatibility fixtures: expected {expected}, found {actual}")
        args.append(f"-PcompatModsDir={mods}")
    log = ROOT / "build" / f"validation-{model.lower()}-{scenario}{suffix}.log"
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(args, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
    content = log.read_text(encoding="utf-8", errors="replace")
    verdict = re.search(r"All (\d+) required tests passed", content)
    # Some loader failures still exit with zero; the explicit completion record is mandatory.
    expected_tests = 5 if model == "DENSITY" else 4
    if result.returncode or not verdict or int(verdict.group(1)) != expected_tests:
        raise RuntimeError(f"{model}/{scenario} did not pass: {log}")
    print(f"{model}/{scenario}: {expected_tests}/{expected_tests} passed ({log})", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--models", nargs="+", choices=("LEGACY", "DENSITY"), default=("LEGACY", "DENSITY"))
    parser.add_argument("--scenarios", nargs="+", choices=SCENARIOS, default=SCENARIOS)
    # 独立目录让默认值平衡验证使用新配置，并保留上一轮运行证据。
    parser.add_argument("--run-tag", default="")
    args = parser.parse_args()
    if args.run_tag and not re.fullmatch(r"[a-z0-9-]+", args.run_tag):
        parser.error("--run-tag must contain only lowercase letters, digits and hyphens")
    for model in args.models:
        for scenario in args.scenarios:
            run_scenario(model, scenario, args.run_tag)


if __name__ == "__main__":
    main()
