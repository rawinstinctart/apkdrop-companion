#!/usr/bin/env python3
"""Check a signed Companion artifact. Never sign, install or activate App Links."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

DEBUG_CERT = "6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e"
DEBUG_PACKAGE = "de.rawinstinctai.apkdrop.debug"
PRODUCTION_PACKAGE = "de.rawinstinctai.apkdrop"
LEGACY_COMPANION_PERMISSIONS = frozenset({
    "android.permission.INTERNET", "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.POST_NOTIFICATIONS", "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.QUERY_ALL_PACKAGES",
})
COMPANION_PERMISSIONS = LEGACY_COMPANION_PERMISSIONS | {"android.permission.ACCESS_NETWORK_STATE"}


def certificate(value):
    normalized = value.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", normalized):
        raise ValueError("Expected certificate must be a SHA-256 fingerprint.")
    return normalized


def signature_facts(output):
    count = re.search(r"^Number of signers: (\d+)\s*$", output, re.M)
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", output, re.M)
    modern = re.search(r"^Verified using v[23](?:\.1)? scheme[^\n]*: true\s*$", output, re.M)
    if not count or int(count.group(1)) != 1 or len(fingerprints) != 1 or not modern:
        raise ValueError("One signer and a verified v2/v3 signature are required.")
    return {"signers": 1, "certificateSha256": certificate(fingerprints[0])}


def permission_facts(output, min_sdk):
    permissions = []
    for line in output.splitlines():
        if not line.startswith("uses-permission"):
            continue
        name_marker = "name='"
        name_start = line.find(name_marker)
        if name_start < 0:
            raise ValueError("Unable to parse a declared Android permission.")
        name_start += len(name_marker)
        name_end = line.find("'", name_start)
        if name_end < 0:
            raise ValueError("Unable to parse a declared Android permission.")
        name = line[name_start:name_end]
        attributes = line[name_end + 1:]
        max_marker = "maxSdkVersion='"
        if "maxSdkVersion" in attributes:
            max_start = attributes.find(max_marker)
            if max_start < 0 or attributes.find(max_marker, max_start + len(max_marker)) >= 0:
                raise ValueError("Unable to parse a permission maxSdkVersion.")
            max_start += len(max_marker)
            max_end = attributes.find("'", max_start)
            if max_end < 0:
                raise ValueError("Unable to parse a permission maxSdkVersion.")
            raw_max = attributes[max_start:max_end]
            if not raw_max.isdecimal():
                raise ValueError("A permission maxSdkVersion is not numeric.")
            if int(raw_max) < min_sdk:
                raise ValueError("A permission maxSdkVersion is below the supported minSdk.")
            raise ValueError("A reviewed Companion permission must not be capped by maxSdkVersion.")
        permissions.append(name)
    return sorted(set(permissions))


def manifest_facts(output):
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", output, re.M)
    minimum = re.search(r"^sdkVersion:'([0-9]+)'\s*$", output, re.M)
    target = re.search(r"^targetSdkVersion:'([0-9]+)'\s*$", output, re.M)
    if not package or not minimum or not target:
        raise ValueError("Package, numeric version and SDK metadata are required.")
    return {"package": package.group(1), "versionCode": int(package.group(2)),
            "versionName": package.group(3), "minSdk": int(minimum.group(1)),
            "targetSdk": int(target.group(1)),
            "debuggable": bool(re.search(r"^application-debuggable\s*$", output, re.M)),
            "permissions": permission_facts(output, int(minimum.group(1)))}


def policy(profile, expected_cert=None, version_code=None, version_name=None):
    if profile == "alpha5":
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 5 identity is fixed; overrides are not accepted.")
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 6, "versionName": "0.1.0-alpha.5-debug", "debuggable": True}
    if profile == "alpha6":
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 6 identity is fixed; overrides are not accepted.")
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 7, "versionName": "0.1.0-alpha.6-debug", "debuggable": True}
    if profile in ("alpha6.1-debug", "alpha6.1-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 6.1 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha6.1-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 8, "versionName": "0.1.0-alpha.6.1-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha7-debug", "alpha7-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 7 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha7-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 9, "versionName": "0.1.0-alpha.7-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha8-debug", "alpha8-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 8 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha8-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 10, "versionName": "0.1.0-alpha.8-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha15.2-debug", "alpha15.2-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 15.2 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha15.2-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 20, "versionName": "0.1.0-alpha.15.2-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha15.1-debug", "alpha15.1-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 15.1 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha15.1-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 19, "versionName": "0.1.0-alpha.15.1-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha15-debug", "alpha15-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 15 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha15-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 18, "versionName": "0.1.0-alpha.15-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha14-debug", "alpha14-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 14 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha14-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 17, "versionName": "0.1.0-alpha.14-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha13-debug", "alpha13-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 13 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha13-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 16, "versionName": "0.1.0-alpha.13-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha12-debug", "alpha12-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 12 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha12-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 15, "versionName": "0.1.0-alpha.12-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha11-debug", "alpha11-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 11 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha11-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 14, "versionName": "0.1.0-alpha.11-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha10-debug", "alpha10-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 10 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha10-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 13, "versionName": "0.1.0-alpha.10-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha9.1-debug", "alpha9.1-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 9.1 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha9.1-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 12, "versionName": "0.1.0-alpha.9.1-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile in ("alpha9-debug", "alpha9-preview"):
        if any(value is not None for value in (expected_cert, version_code, version_name)):
            raise ValueError("Alpha 9 identity is fixed; overrides are not accepted.")
        is_debug = profile == "alpha9-debug"
        return {"package": DEBUG_PACKAGE, "certificateSha256": DEBUG_CERT,
                "versionCode": 11, "versionName": "0.1.0-alpha.9-" + ("debug" if is_debug else "preview"),
                "debuggable": is_debug}
    if profile != "production":
        raise ValueError("Unknown release profile.")
    if expected_cert is None or version_code is None or not version_name:
        raise ValueError("Production requires an explicit certificate, version code and version name.")
    expected_cert = certificate(expected_cert)
    if expected_cert == DEBUG_CERT:
        raise ValueError("The original debug certificate cannot be used for production.")
    if version_code < 6:
        raise ValueError("Production versionCode must be at least 6.")
    if re.search(r"debug", version_name, re.I):
        raise ValueError("A debug version name cannot be used for production.")
    return {"package": PRODUCTION_PACKAGE, "certificateSha256": expected_cert,
            "versionCode": version_code, "versionName": version_name, "debuggable": False}


def run_tool(tool, arguments):
    result = subprocess.run([str(tool), *arguments], capture_output=True, text=True,
                            timeout=60, env={**os.environ, "LC_ALL": "C"})
    if result.returncode != 0:
        # APK diagnostics are not a proof of successful verification.
        raise ValueError(tool.name + " verification failed.")
    return result.stdout


def digest(apk):
    with apk.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def inspect(apk, tools, expected):
    report = {"schema": "apkdrop.companion.release-check.v1", "artifactStatus": "blocked",
              "checks": {}, "errors": [],
              "remainingGates": {"physicalDeviceAcceptance": "open", "hostedAndroidCI": "unverified",
                                 "productionAppLinks": "not_activated"}}
    try:
        if not apk.is_file() or not 0 < apk.stat().st_size <= 128 * 1024 * 1024:
            raise ValueError("A Companion APK of at most 128 MiB is required.")
        before = digest(apk)
        report["artifact"] = {"filename": apk.name, "size": apk.stat().st_size, "sha256": before}
        signature = signature_facts(run_tool(tools / "apksigner", ["verify", "--verbose", "--print-certs", str(apk)]))
        report["signature"] = signature
        report["checks"]["cryptographicSignature"] = "passed"
        manifest = manifest_facts(run_tool(tools / "aapt", ["dump", "badging", str(apk)]))
        report["manifest"] = manifest
        actual = {**manifest, **signature}
        for field, value in expected.items():
            passed = actual.get(field) == value
            report["checks"][field] = "passed" if passed else "blocked"
            if not passed:
                report["errors"].append(field + " does not match the required release identity.")
        sdk_ok = manifest["minSdk"] == 26 and manifest["targetSdk"] == 36
        report["checks"]["sdk"] = "passed" if sdk_ok else "blocked"
        if not sdk_ok:
            report["errors"].append("Companion minSdk 26 and targetSdk 36 are required.")
        permissions_expected = (LEGACY_COMPANION_PERMISSIONS if expected.get("package") == DEBUG_PACKAGE
                                and expected.get("versionCode", 0) <= 11 else COMPANION_PERMISSIONS)
        permissions_ok = set(manifest["permissions"]) == permissions_expected
        report["checks"]["permissions"] = "passed" if permissions_ok else "blocked"
        if not permissions_ok:
            report["errors"].append("Companion permissions do not match the reviewed allowlist.")
        after = digest(apk)
        report["checks"]["artifactUnchanged"] = "passed" if before == after else "blocked"
        if before != after:
            report["errors"].append("APK changed during verification.")
        if not report["errors"]:
            report["artifactStatus"] = "passed"
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        report["errors"].append(str(error) if isinstance(error, ValueError) else "Verification tool unavailable or timed out.")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--profile", choices=["alpha5", "alpha6", "alpha6.1-debug", "alpha6.1-preview", "alpha7-debug", "alpha7-preview", "alpha8-debug", "alpha8-preview", "alpha9-debug", "alpha9-preview", "alpha9.1-debug", "alpha9.1-preview", "alpha10-debug", "alpha10-preview", "alpha11-debug", "alpha11-preview", "alpha12-debug", "alpha12-preview", "alpha13-debug", "alpha13-preview", "alpha14-debug", "alpha14-preview", "alpha15-debug", "alpha15-preview", "alpha15.1-debug", "alpha15.1-preview", "alpha15.2-debug", "alpha15.2-preview", "production"], default="alpha15-preview")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--build-tools", default="35.0.0")
    parser.add_argument("--expected-cert-sha256")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    parser.add_argument("--report", type=Path, help="Create a new JSON report; existing files are never overwritten.")
    args = parser.parse_args(argv)
    try:
        if not args.sdk:
            raise ValueError("ANDROID_HOME or --sdk is required.")
        if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", args.build_tools):
            raise ValueError("An explicit numeric build-tools version is required.")
        expected = policy(args.profile, args.expected_cert_sha256, args.version_code, args.version_name)
        report = inspect(args.apk.resolve(), Path(args.sdk) / "build-tools" / args.build_tools, expected)
        report["profile"] = args.profile
        encoded = json.dumps(report, indent=2) + "\n"
        if args.report:
            with args.report.open("x", encoding="utf-8") as destination:
                destination.write(encoded)
        print(encoded, end="")
        return 0 if report["artifactStatus"] == "passed" else 2
    except (ValueError, OSError) as error:
        print(str(error) if isinstance(error, ValueError) else "Report could not be created; existing files are preserved.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
