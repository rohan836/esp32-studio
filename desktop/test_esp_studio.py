import unittest

from esp_studio import command_doctor, fqbn_core


class FqbnTests(unittest.TestCase):
    def test_core_id(self) -> None:
        self.assertEqual(fqbn_core("arduino:avr:uno"), "arduino:avr")

    def test_module_import_does_not_run_cli(self) -> None:
        self.assertIsNotNone(command_doctor)


if __name__ == "__main__":
    unittest.main()
