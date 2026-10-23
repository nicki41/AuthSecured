#!/usr/bin/env python3
"""Upload AuthSecured release JARs to Modrinth, one version per file.

Each upload carries the full changelog from MODRINTH_CHANGELOG_<version>.md.
Versions that already exist on Modrinth are skipped, so the script can be
re-run safely after a partial failure.

Token: MODRINTH_TOKEN environment variable, or the file named by
MODRINTH_TOKEN_FILE (default ~/.config/authsecured/modrinth-token). The token
needs the "Create versions" scope. It is never printed.

Usage:
    scripts/publish_modrinth.py            # dry run: show what would be uploaded
    scripts/publish_modrinth.py --publish  # upload
"""

import argparse
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request
import uuid

API = "https://api.modrinth.com/v2"
PROJECT = "z4naQApQ"  # modrinth.com/plugin/authsecured
FABRIC_API_PROJECT = "P7dR8mSH"
USER_AGENT = "touchfalls/AuthSecured release script (github.com/touchfalls/AuthSecured)"
ROOT = pathlib.Path(__file__).resolve().parent.parent

FABRIC_TARGETS = [
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
    "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
]
PAPER_LOADERS = ["paper", "purpur"]


def project_version() -> str:
    match = re.search(r'version = "([^"]+)"', (ROOT / "build.gradle.kts").read_text())
    if not match:
        sys.exit("Cannot read the project version from build.gradle.kts")
    return match.group(1)


def load_token() -> str:
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if token:
        return token
    path = pathlib.Path(os.environ.get(
        "MODRINTH_TOKEN_FILE", "~/.config/authsecured/modrinth-token")).expanduser()
    if path.is_file():
        return path.read_text().strip()
    sys.exit(f"No Modrinth token: set MODRINTH_TOKEN or create {path}")


def request(method: str, url: str, token: str = None, body: bytes = None, content_type: str = None):
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("User-Agent", USER_AGENT)
    if token:
        req.add_header("Authorization", token)
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:500]
        raise RuntimeError(f"{method} {url} failed: HTTP {error.code} {detail}") from None


def existing_version_numbers() -> set:
    versions = request("GET", f"{API}/project/{PROJECT}/version")
    return {version["version_number"] for version in versions}


def multipart(data: dict, file_path: pathlib.Path):
    boundary = uuid.uuid4().hex
    parts = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"data\"\r\n"
        f"Content-Type: application/json\r\n\r\n".encode() + json.dumps(data).encode() + b"\r\n",
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{file_path.name}\"\r\n"
        f"Content-Type: application/java-archive\r\n\r\n".encode() + file_path.read_bytes() + b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ]
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def plan(version: str, changelog: str):
    libs = ROOT / "build" / "libs"
    uploads = [{
        "file": libs / f"authsecured-paper-{version}.jar",
        "data": {
            "version_number": version,
            "loaders": PAPER_LOADERS,
            "game_versions": FABRIC_TARGETS,
            "dependencies": [],
        },
    }]
    for minecraft in FABRIC_TARGETS:
        uploads.append({
            "file": libs / f"authsecured-fabric-{version}+mc{minecraft}.jar",
            "data": {
                "version_number": f"{version}+mc{minecraft}",
                "loaders": ["fabric"],
                "game_versions": [minecraft],
                "dependencies": [{"project_id": FABRIC_API_PROJECT, "dependency_type": "required"}],
            },
        })
    for upload in uploads:
        upload["data"].update({
            "name": f"AuthSecured {version}",
            "changelog": changelog,
            "version_type": "release",
            "featured": False,
            "status": "listed",
            "project_id": PROJECT,
            "file_parts": ["file"],
            "primary_file": "file",
        })
    return uploads


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--publish", action="store_true", help="upload instead of a dry run")
    args = parser.parse_args()

    version = project_version()
    changelog_path = ROOT / f"MODRINTH_CHANGELOG_{version}.md"
    if not changelog_path.is_file():
        sys.exit(f"Missing {changelog_path.name}")
    changelog = changelog_path.read_text().strip()

    uploads = plan(version, changelog)
    missing = [str(upload["file"].relative_to(ROOT)) for upload in uploads if not upload["file"].is_file()]
    if missing:
        sys.exit("Missing release files:\n  " + "\n  ".join(missing))

    published = existing_version_numbers()
    token = load_token() if args.publish else None
    for upload in uploads:
        number = upload["data"]["version_number"]
        label = f"{number:<18} {upload['file'].name}"
        if number in published:
            print(f"skip    {label} (already on Modrinth)")
            continue
        if not args.publish:
            print(f"would   {label} loaders={upload['data']['loaders']} "
                  f"games={len(upload['data']['game_versions'])}")
            continue
        body, content_type = multipart(upload["data"], upload["file"])
        created = request("POST", f"{API}/version", token, body, content_type)
        print(f"created {label} id={created['id']}")
    if not args.publish:
        print("Dry run only. Re-run with --publish to upload.")


if __name__ == "__main__":
    main()
