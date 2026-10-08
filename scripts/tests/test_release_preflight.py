"""Policy/parser tests. Mock outputs do not claim cryptographic APK verification."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("preflight", Path(__file__).parents[1] / "release-preflight.py")
p = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(p)
SIGNATURE = ("Verifies\nVerified using v1 scheme (JAR signing): false\n"
             "Verified using v2 scheme (APK Signature Scheme v2): true\n"
             "Number of signers: 1\nSigner #1 certificate SHA-256 digest: " + p.DEBUG_CERT + "\n")
MANIFEST = ("package: name='de.rawinstinctai.apkdrop.debug' versionCode='6' "
            "versionName='0.1.0-alpha.5-debug'\nsdkVersion:'26'\ntargetSdkVersion:'36'\n"
            "application-debuggable\n")


class ReleasePolicyTest(unittest.TestCase):
    def test_alpha_identity_cannot_be_overridden(self):
        for kwargs in [{"expected_cert": "a" * 64}, {"version_code": 7}, {"version_name": "other"}]:
            with self.assertRaises(ValueError):
                p.policy("alpha5", **kwargs)

    def test_alpha6_identity_is_pinned_and_rejects_alpha5_artifact(self):
        alpha6 = p.policy("alpha6")
        self.assertEqual(alpha6["versionCode"], 7)
        self.assertEqual(alpha6["versionName"], "0.1.0-alpha.6-debug")
        for kwargs in [{"expected_cert": "a" * 64}, {"version_code": 6}, {"version_name": "other"}]:
            with self.assertRaises(ValueError):
                p.policy("alpha6", **kwargs)
        self.assertEqual(p.policy("alpha5")["versionCode"], 6)

    def test_production_needs_explicit_identity(self):
        with self.assertRaises(ValueError):
            p.policy("production")

    def test_debug_signer_never_becomes_production(self):
        for fingerprint in [p.DEBUG_CERT, ":".join(p.DEBUG_CERT[i:i+2] for i in range(0, 64, 2)).upper()]:
            with self.assertRaises(ValueError):
                p.policy("production", fingerprint, 6, "0.1.0")

    def test_invalid_fingerprint_is_rejected(self):
        for value in ["", "a" * 63, "z" * 64, "a" * 65]:
            with self.assertRaises(ValueError):
                p.certificate(value)

    def test_production_rejects_downgrade_and_debug_name(self):
        for version, name in [(5, "0.1.0"), (6, "0.1.0-DEBUG")]:
            with self.assertRaises(ValueError):
                p.policy("production", "a" * 64, version, name)

    def test_production_package_is_fixed(self):
        self.assertEqual(p.policy("production", "a" * 64, 6, "0.1.0")["package"], p.PRODUCTION_PACKAGE)

    def test_multiple_signers_are_rejected(self):
        with self.assertRaises(ValueError):
            p.signature_facts(SIGNATURE.replace("signers: 1", "signers: 2") +
                              "Signer #2 certificate SHA-256 digest: " + "a" * 64 + "\n")

    def test_unsigned_and_v1_only_outputs_are_rejected(self):
        for output in ["", SIGNATURE.replace("v2): true", "v2): false")]:
            with self.assertRaises(ValueError):
                p.signature_facts(output)

    def test_missing_certificate_is_rejected(self):
        with self.assertRaises(ValueError):
            p.signature_facts(SIGNATURE.replace(p.DEBUG_CERT, "unknown"))

    def test_missing_sdk_metadata_is_rejected(self):
        with self.assertRaises(ValueError):
            p.manifest_facts(MANIFEST.replace("targetSdkVersion:'36'", ""))


class ArtifactGateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apk = self.root / "parser-fixture.apk"
        self.apk.write_bytes(b"Parser fixture, not a cryptographic APK.")

    def inspect_with(self, signature=SIGNATURE, manifest=MANIFEST, expected=None):
        with patch.object(p, "run_tool", side_effect=[signature, manifest]):
            return p.inspect(self.apk, self.root, expected or p.policy("alpha5"))

    def test_artifact_pass_keeps_real_world_gates_open(self):
        result = self.inspect_with()
        self.assertEqual(result["artifactStatus"], "passed")
        self.assertEqual(result["remainingGates"]["physicalDeviceAcceptance"], "open")
        self.assertEqual(result["remainingGates"]["hostedAndroidCI"], "unverified")
        self.assertEqual(result["remainingGates"]["productionAppLinks"], "not_activated")

    def test_wrong_package_signer_version_and_sdk_block_output(self):
        variants = [
            (SIGNATURE.replace(p.DEBUG_CERT, "a" * 64), MANIFEST),
            (SIGNATURE, MANIFEST.replace(p.DEBUG_PACKAGE, "another.application")),
            (SIGNATURE, MANIFEST.replace("versionCode='6'", "versionCode='4'")),
            (SIGNATURE, MANIFEST.replace("alpha.5", "alpha.4")),
            (SIGNATURE, MANIFEST.replace("sdkVersion:'26'", "sdkVersion:'25'")),
            (SIGNATURE, MANIFEST.replace("targetSdkVersion:'36'", "targetSdkVersion:'35'")),
        ]
        for signature, manifest in variants:
            with self.subTest(manifest=manifest):
                self.assertEqual(self.inspect_with(signature, manifest)["artifactStatus"], "blocked")

    def test_alpha6_requires_new_version(self):
        alpha5 = self.inspect_with(expected=p.policy("alpha6"))
        self.assertEqual(alpha5["artifactStatus"], "blocked")
        self.assertEqual(alpha5["checks"]["versionCode"], "blocked")
        self.assertEqual(alpha5["checks"]["versionName"], "blocked")
        alpha6_manifest = MANIFEST.replace("versionCode='6'", "versionCode='7'").replace("alpha.5", "alpha.6")
        alpha6 = self.inspect_with(manifest=alpha6_manifest, expected=p.policy("alpha6"))
        self.assertEqual(alpha6["artifactStatus"], "passed")

    def test_production_debuggable_apk_is_blocked(self):
        signature = SIGNATURE.replace(p.DEBUG_CERT, "a" * 64)
        manifest = MANIFEST.replace(p.DEBUG_PACKAGE, p.PRODUCTION_PACKAGE).replace("0.1.0-alpha.5-debug", "0.1.0")
        result = self.inspect_with(signature, manifest, p.policy("production", "a" * 64, 6, "0.1.0"))
        self.assertEqual(result["checks"]["debuggable"], "blocked")

    def test_changed_apk_during_verification_is_blocked(self):
        def tool_output(tool, arguments):
            if tool.name == "apksigner":
                return SIGNATURE
            self.apk.write_bytes(b"changed after signature verification")
            return MANIFEST
        with patch.object(p, "run_tool", side_effect=tool_output):
            result = p.inspect(self.apk, self.root, p.policy("alpha5"))
        self.assertEqual(result["checks"]["artifactUnchanged"], "blocked")

    def test_tool_failure_is_not_success(self):
        with patch.object(p, "run_tool", side_effect=ValueError("apksigner verification failed.")):
            result = p.inspect(self.apk, self.root, p.policy("alpha5"))
        self.assertEqual(result["artifactStatus"], "blocked")
        self.assertNotIn("cryptographicSignature", result["checks"])

    def test_missing_artifact_is_blocked(self):
        self.apk.unlink()
        self.assertEqual(p.inspect(self.apk, self.root, p.policy("alpha5"))["artifactStatus"], "blocked")

    def test_report_is_not_overwritten(self):
        report = self.root / "report.json"
        report.write_text("existing evidence")
        with patch.object(p, "inspect", return_value={"artifactStatus": "passed"}):
            self.assertEqual(p.main([str(self.apk), "--sdk", str(self.root), "--report", str(report)]), 2)
        self.assertEqual(report.read_text(), "existing evidence")


if __name__ == "__main__":
    unittest.main()
