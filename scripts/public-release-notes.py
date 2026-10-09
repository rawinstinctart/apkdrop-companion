#!/usr/bin/env python3
"""Public GitHub release notes: keep internal acceptance work out of public copy.

Only release-note text is ever changed. APK files, tags, checksums and draft
releases are never modified by the live cleanup.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
from urllib.request import Request, urlopen

# Explicit, reviewed removals of internal QA / future-work notes.
# Keep public-facing security and compatibility disclosures intact.
REMOVALS = (
    (r"(?m)^\*\*Noch offen:\*\*[^\r\n]*(?:\r?\n)?", ""),
    (r" Physical device acceptance remains OPEN\.", ""),
    (
        r"Device acceptance and Play Protect outcome are \*\*OPEN\*\*; this release does not guarantee",
        "This release does not guarantee",
    ),
    (
        r"(?m)^Screenshots in den Prüfarbelegen nutzen synthetische Demo-Apps\. Test-Wiederherstellung prüft explizit save/destroy/create; die simulierte Animation wird nur in UI-Tests deaktiviert\. Physische Geräteprüfung für Upgrade, Rotation, Sharesheet, Hintergrundjobs und Installer bleibt offen\. Produktions-App-Links sind nicht aktiviert\.(?:\r?\n)?",
        "Produktions-App-Links sind in dieser Preview nicht aktiviert.\n",
    ),
    (
        r"(?m)^GitHub Actions did not run job steps because GitHub reported an account payment/spending-limit problem\. This release does not claim a passing hosted CI run, physical-device acceptance, production approval or malware certification\.",
        "This release does not claim a passing hosted CI run, production approval or malware certification.",
    ),

    (r"(?m)^\*\*Hinweis:\*\* Die Installation und visuelle Abnahme[^\r\n]*(?:\r?\n)?", ""),
    (r"(?m)^\*\*Geräteabnahme bleibt offen:\*\*[^\r\n]*(?:\r?\n)?", ""),
    (
        r"(?m)^\*\*Physical device acceptance remains OPEN:\*\*[^\r\n]*(?:\r?\n)?",
        "**Hinweis:** Dies ist eine Preview, keine Produktionsfreigabe oder Play-Protect-Zertifizierung.\n",
    ),
    (r"\*\*Gerätetest:\*\* Noch offen\. ?", ""),
    (r"(?m)^## Offene Geräte-Abnahme\r?\n[^\r\n]*(?:\r?\n)?", ""),
    (r" Ein echter Android-Gerätetest bleibt separat offen\.", ""),
    (r" Physischer Android-Gerätetest bleibt offen\.", ""),
    (
        r"(?m)^Physical device acceptance \(in-place update, app launch and real-account GitHub connection\) remains to be performed\. Self-update and import of installed app inventory are planned for Alpha 15\.(?:\r?\n)?",
        "",
    ),
    (
        r" Die Installation und Verbindung mit einem echten Konto auf einem physischen Android-Gerät bleiben noch zu testen\.",
        "",
    ),
    (
        r"(?m)^Physische Sharesheet-, Upgrade-, Rotations- und Hintergrundjob-Prüfung bleibt offen\. Native Vorschauen nutzen synthetische Demo-Daten\.(?:\r?\n)?",
        "",
    ),
)

BLOCKED = (
    (re.compile(r"(?i)\bnoch offen\b"), "offene interne Aufgaben"),
    (re.compile(r"(?i)\b(?:geräteabnahme|gerätetest|android.gerätetest)\b"), "interne Geräteabnahme"),
    (re.compile(r"(?i)\bphysical device acceptance\b"), "interne Geräteabnahme"),
    (re.compile(r"(?i)\bfixture.tests ersetzen\b"), "interne Testnotiz"),
    (re.compile(r"(?i)\b(?:1\.0.gate|freigabegate|arbeitspaket(?:e)?)\b"), "interne Planung"),
    (re.compile(r"(?i)\breal(?:em|en)\s+github.konto\b"), "interne Kontenabnahme"),
)

# Published release bodies are checked after publish/edit; drafts are never touched.

def sanitize(body: str) -> str:
    result = body
    for pattern, replacement in REMOVALS:
        result = re.sub(pattern, replacement, result)
    if result == body:
        return body  # Do not rewrite clean release descriptions.
    # Avoid empty paragraphs caused by deleted notes, without rewriting prose.
    result = re.sub(r"\n{3,}", "\n\n", result)
    return result.rstrip() + "\n" if result.strip() else ""

def violations(body: str) -> list[str]:
    return sorted({message for regex, message in BLOCKED if regex.search(body)})

def github_api(method: str, url: str, data=None):
    token = os.environ.get("GH_TOKEN")
    if not token:
        raise RuntimeError("GH_TOKEN required to edit GitHub releases")
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "APKDrop-release-copy-policy",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    payload = json.dumps(data).encode("utf-8") if data is not None else None
    if payload is not None:
        headers["Content-Type"] = "application/json"
    with urlopen(Request(url, data=payload, headers=headers, method=method), timeout=30) as response:
        return json.load(response)

def repair_live():
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if repo != "rawinstinctart/apkdrop-companion":
        raise RuntimeError("Repository does not match the allowed APKDrop repository")
    seen = set()
    for page in range(1, 6):
        releases = github_api("GET", f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}")
        for release in releases:
            tag = release.get("tag_name")
            if not tag or release.get("draft") or release.get("immutable"):
                continue
            seen.add(tag)
            original = release.get("body") or ""
            updated = sanitize(original)
            if updated == original:
                print(f"UNCHANGED {tag}")
                continue
            url = f"https://api.github.com/repos/{repo}/releases/{release['id']}"
            github_api("PATCH", url, {"body": updated})
            confirmed = github_api("GET", url)
            if confirmed.get("body") != updated:
                raise RuntimeError(f"Verification failed for {tag}")
            print(f"UPDATED {tag} (notes only)")
        if len(releases) < 100:
            break
    print(f"Checked {len(seen)} public releases; APK assets and tags untouched.")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair-live", action="store_true", help="GitHub Actions only: edit approved published release descriptions")
    parser.add_argument("files", nargs="*", help="Explicit public notes files; default: release/*-notes.md")
    args = parser.parse_args()
    if args.repair_live:
        if args.files:
            parser.error("--repair-live does not accept files")
        repair_live()
        return
    files = [Path(name) for name in args.files] if args.files else sorted(Path("release").glob("*-notes.md"))
    if not files:
        raise SystemExit("No release-notes files found")
    found = False
    for path in files:
        body = path.read_text(encoding="utf-8")
        problems = violations(body)
        if problems:
            print(f"{path}: REJECTED public release copy: {', '.join(problems)}", file=sys.stderr)
            found = True
        else:
            print(f"{path}: OK")
    if found:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
