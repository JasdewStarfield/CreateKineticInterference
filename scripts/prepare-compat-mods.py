"""Download pinned optional mods for isolated GameTests, checking Modrinth SHA-512."""

import hashlib
import json
from pathlib import Path
import urllib.request


ROOT = Path(__file__).resolve().parents[1] / "build" / "compat-mods"
# These are NeoForge 1.21.1 releases; never silently substitute newer versions.
VERSIONS = {"picky": "ws15FV2y", "flowing": "k37oVEnG"}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "CreateKineticInterference-compat-tests"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def main():
    manifest = []
    for scenario, version_id in VERSIONS.items():
        version = json.loads(fetch(f"https://api.modrinth.com/v2/version/{version_id}"))
        artifact = next(file for file in version["files"] if file["primary"])
        filename = artifact["filename"]
        if Path(filename).name != filename:
            raise ValueError("Expected a plain artifact filename")
        payload = fetch(artifact["url"])
        digest = hashlib.sha512(payload).hexdigest()
        if digest != artifact["hashes"]["sha512"]:
            raise ValueError(f"Hash mismatch: {filename}")
        # Keep pairwise and all-three samples separate, without touching any player mods directory.
        for destination in (scenario, "both"):
            folder = ROOT / destination
            folder.mkdir(parents=True, exist_ok=True)
            (folder / filename).write_bytes(payload)
        manifest.append({"version_id": version_id, "filename": filename,
                         "url": artifact["url"], "sha512": digest})
        print(f"Verified {filename}")
    (ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
