import importlib.util
import pathlib
import tempfile
import unittest

module_spec = importlib.util.spec_from_file_location("runtime", pathlib.Path(__file__).resolve().parents[1] / "scripts/prepare-java-runtime.py")
runtime = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(runtime)


class RuntimeSettingsTest(unittest.TestCase):
    def test_no_proxy_keeps_normal_local_runtime(self):
        self.assertEqual(runtime.settings({})["services"]["backend"]["environment"], {})

    def test_proxy_ca_are_forwarded_without_api_keys(self):
        with tempfile.NamedTemporaryFile() as cert:
            value = runtime.settings({"HTTPS_PROXY": "http://proxy:8080", "CODEX_PROXY_CERT": cert.name,
                                      "GEMINI_API_KEY": "never-serialize-this"}, resolve=lambda host: "192.0.2.1")
            backend = value["services"]["backend"]
            self.assertEqual(backend["environment"]["JAVA_HTTPS_PROXY_HOST"], "proxy")
            self.assertEqual(backend["extra_hosts"], ["proxy:192.0.2.1"])
            self.assertTrue(backend["volumes"][0].endswith(":ro"))
            self.assertNotIn("never-serialize-this", str(value))

    def test_authenticated_proxy_is_rejected_without_echoing_it(self):
        with self.assertRaises(ValueError) as error:
            runtime.settings({"HTTPS_PROXY": "http://private-user:private-password@proxy:8080"})
        self.assertNotIn("private-password", str(error.exception))

    def test_missing_ca_is_rejected(self):
        with self.assertRaises(ValueError):
            runtime.settings({"CODEX_PROXY_CERT": "/nonexistent/runtime-ca.pem"})


if __name__ == "__main__":
    unittest.main()
