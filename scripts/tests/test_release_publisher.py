"""The public GitHub publisher must accept only non-debuggable Alpha previews."""
from pathlib import Path
import subprocess
import unittest

PUBLISHER = Path(__file__).parents[1] / "publish-github-prerelease.sh"


class PublisherProfileTest(unittest.TestCase):
    def run_publisher(self, profile):
        return subprocess.run(
            ["bash", str(PUBLISHER), "v0.1.0-alpha.6.1", "/tmp/missing-signed-preview.apk",
             profile, "Preview", "/tmp/missing-release-notes.md", "e92ff1b57f5ae6709ff6d3b6f86acac1471aaf8d"],
            capture_output=True, text=True, check=False)

    def test_signer_accepts_alpha12_profiles_without_changing_public_release_gate(self):
        signer = (Path(__file__).parents[1] / "sign-existing-debug.sh").read_text()
        self.assertIn("alpha12-debug|alpha12-preview|alpha13-debug|alpha13-preview|alpha14-debug|alpha14-preview) ;;", signer)
        self.assertIn("alpha12-preview|alpha13-preview|alpha14-preview|production)", PUBLISHER.read_text())
        self.assertNotIn("alpha12-debug|production)", PUBLISHER.read_text())

    def test_alpha13_signing_and_publishing_keeps_debug_out_of_public_channel(self):
        signer=(Path(__file__).parents[1] / "sign-existing-debug.sh").read_text()
        self.assertIn("alpha13-debug|alpha13-preview|alpha14-debug|alpha14-preview) ;;",signer)
        preview=self.run_publisher("alpha13-preview")
        self.assertEqual(preview.returncode,2)
        self.assertIn("Signed APK is missing",preview.stderr)
        debug=self.run_publisher("alpha13-debug")
        self.assertEqual(debug.returncode,2)
        self.assertIn("Unknown release profile",debug.stderr)

    def test_debug_profile_is_rejected_and_preview_profile_is_supported(self):
        debug = self.run_publisher("alpha7-debug")
        self.assertEqual(debug.returncode, 2)
        self.assertIn("Unknown release profile", debug.stderr)

        preview = self.run_publisher("alpha11-preview")
        self.assertEqual(preview.returncode, 2)
        self.assertIn("Signed APK is missing", preview.stderr)
        self.assertNotIn("Unknown release profile", preview.stderr)


if __name__ == "__main__":
    unittest.main()
