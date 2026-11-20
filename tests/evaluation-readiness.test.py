import importlib.util
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("evaluation", pathlib.Path(__file__).resolve().parents[1] / "scripts/check-sfr003-evaluation.py")
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class ReadinessTest(unittest.TestCase):
    def test_null_is_a_truth_value_but_blank_is_not(self):
        self.assertTrue(evaluation.truth_valid("DepositWon", "null"))
        self.assertTrue(evaluation.truth_valid("DepositWon", "0"))
        self.assertFalse(evaluation.truth_valid("DepositWon", ""))
        self.assertFalse(evaluation.truth_valid("DepositWon", "3.5"))
        self.assertFalse(evaluation.truth_valid("AreaSqm", "NaN"))

    def test_files_privacy_and_human_truth_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            base = pathlib.Path(directory)
            (base / "ad.txt").write_text("가상 평가 자료")
            row = {"sampleId": "eval-1", "sampleType": "representative", "privacyChecked": "yes",
                   "truthReviewedBy": "reviewer", "sourceFile": "ad.txt", "expectedRawTextFile": "ad.txt",
                   **{"expected" + field: "null" for field in evaluation.STRUCTURED}}
            self.assertEqual(evaluation.check([row], base)["readySamples"], 1)
            self.assertEqual(evaluation.check([{**row, "sampleType": "synthetic"}], base)["readySamples"], 0)
            self.assertEqual(evaluation.check([{**row, "privacyChecked": ""}], base)["readySamples"], 0)
            self.assertEqual(evaluation.check([{**row, "sourceFile": "missing.txt"}], base)["readySamples"], 0)
            self.assertIsNone(evaluation.check([row], base)["accuracy"])
            duplicate = evaluation.check([row, {**row, "sampleId": "eval-2"}], base)
            self.assertEqual(duplicate["readySamples"], 1)
            self.assertIn("uniqueSourceContent", duplicate["issues"][0]["missingOrInvalid"])


if __name__ == "__main__":
    unittest.main()
