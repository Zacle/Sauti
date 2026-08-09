import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate-restore-target.py"
SPEC = importlib.util.spec_from_file_location("restore_validator", MODULE_PATH)
assert SPEC and SPEC.loader
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class RestoreTargetValidatorTest(unittest.TestCase):
    def test_rejects_the_exact_production_database(self):
        production = {"NEON_DATABASE_URL": "postgresql://owner:secret@prod.example/sauti"}
        drill = {
            "SAUTI_RESTORE_DATABASE_URL": "postgresql://other:secret@prod.example/sauti",
            "SAUTI_RESTORE_CONFIRM": "isolated-restore-drill",
        }

        with self.assertRaisesRegex(ValueError, "production database"):
            VALIDATOR.validate_restore_target(production, drill)

    def test_rejects_a_target_without_an_explicit_disposable_name(self):
        production = {"NEON_DATABASE_URL": "postgresql://owner:secret@prod.example/sauti"}
        drill = {
            "SAUTI_RESTORE_DATABASE_URL": "postgresql://owner:secret@other.example/sauti",
            "SAUTI_RESTORE_CONFIRM": "isolated-restore-drill",
        }

        with self.assertRaisesRegex(ValueError, "database name"):
            VALIDATOR.validate_restore_target(production, drill)

    def test_accepts_an_explicit_isolated_restore_database(self):
        production = {"NEON_DATABASE_URL": "postgresql://owner:secret@prod.example/sauti"}
        drill = {
            "SAUTI_RESTORE_DATABASE_URL": "postgresql://owner:secret@isolated.example/sauti_restore_drill",
            "SAUTI_RESTORE_CONFIRM": "isolated-restore-drill",
        }

        VALIDATOR.validate_restore_target(production, drill)

    def test_fails_closed_when_the_production_identity_is_missing(self):
        drill = {
            "SAUTI_RESTORE_DATABASE_URL": "postgresql://owner:secret@isolated.example/sauti_restore_drill",
            "SAUTI_RESTORE_CONFIRM": "isolated-restore-drill",
        }

        with self.assertRaisesRegex(ValueError, "NEON_DATABASE_URL"):
            VALIDATOR.validate_restore_target({}, drill)


if __name__ == "__main__":
    unittest.main()
