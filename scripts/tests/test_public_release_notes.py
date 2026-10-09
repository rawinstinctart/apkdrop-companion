"""Regression tests for user-facing public GitHub release notes."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "public-release-notes.py"
spec = importlib.util.spec_from_file_location("public_release_notes", SCRIPT)
notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes)

class PublicReleaseNoteTests(unittest.TestCase):
    def test_alpha17_internal_status_removed(self):
        public = "# Alpha 17\n\nNeue Funktionen.\n\n"
        internal = "**Noch offen:** Gerätetest und privater Import. Alpha 18 bleibt im 1.0-Gate #33.\n"
        cleaned = notes.sanitize(public + internal)
        self.assertEqual(cleaned, "# Alpha 17\n\nNeue Funktionen.\n")
        self.assertFalse(notes.violations(cleaned))

    def test_legacy_play_protect_warning_remains(self):
        original = (
            "Device acceptance and Play Protect outcome are **OPEN**; this release "
            "does not guarantee that a Play Protect warning will disappear. "
            "Signature verification is not malware certification."
        )
        cleaned = notes.sanitize(original)
        self.assertIn("Play Protect warning", cleaned)
        self.assertIn("not malware certification", cleaned)
        self.assertNotIn("acceptance", cleaned)

    def test_clean_notes_are_not_rewritten(self):
        clean = "# Alpha 18\n\nVerbesserte Update-Ansicht.\n"
        self.assertEqual(notes.sanitize(clean), clean)
        self.assertFalse(notes.violations(clean))

    def test_internal_release_copy_is_rejected(self):
        cases = (
            "**Noch offen:** Gerätetest",
            "Physical device acceptance remains OPEN",
            "Fixture-Tests ersetzen diese Abnahme nicht.",
            "Das 1.0-Gate bleibt in #33.",
            "Alpha 18 ist ein Arbeitspaket.",
        )
        for note in cases:
            with self.subTest(note=note):
                self.assertTrue(notes.violations(note))

if __name__ == "__main__":
    unittest.main()
