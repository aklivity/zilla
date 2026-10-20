#
# Copyright 2021-2026 Aklivity Inc
#
# Licensed under the Aklivity Community License (the "License"); you may not use
# this file except in compliance with the License.  You may obtain a copy of the
# License at
#
#   https://www.aklivity.io/aklivity-community-license/
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#

import json
import os
import tempfile
import unittest
from pathlib import Path

from .. import scan_secrets, setup
from ..redact import Scrubber
from .harness import REAL, run_against


def providers():
    return json.loads((REAL / "providers.json").read_text())["providers"]


class SetupTests(unittest.TestCase):
    def test_tier_expands_and_reference_is_added(self):
        chosen = [p["id"] for p in setup.expand("p1", providers())]
        self.assertEqual(chosen[0], "openai")
        self.assertTrue(all(p["tier"] == "p1" for p in setup.expand("p1", providers()) if p["id"] != "openai"))

    def test_explicit_list_does_not_add_reference(self):
        self.assertEqual([p["id"] for p in setup.expand("groq,ollama", providers())], ["groq", "ollama"])

    def test_first_party_pulls_in_reference(self):
        self.assertEqual([p["id"] for p in setup.expand("gemini-openai", providers())], ["openai", "gemini-openai"])

    def test_unknown_provider_is_rejected(self):
        with self.assertRaises(ValueError):
            setup.expand("nope", providers())

    def test_entry_carries_extra_secrets(self):
        cloudflare = next(p for p in providers() if p["id"] == "cloudflare-gateway")
        entry = setup.entry(cloudflare, 0.5)
        self.assertEqual((entry["extra_secret_1"], entry["extra_secret_2"]), ("CLOUDFLARE_ACCOUNT_ID", ""))


class ScanTests(unittest.TestCase):
    def test_scan_finds_known_values_and_patterns(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "clean.json").write_text('{"authorization": "[REDACTED]"}')
            Path(tmp, "leak.json").write_text('{"x": "' + "sk-" + "abcdefghijklmnopqrstuvwxyz" + '"}')
            Path(tmp, "value.json").write_text("token=hunter2hunter2")
            findings = scan_secrets.scan([tmp], {"hunter2hunter2"})
        self.assertEqual(sorted(Path(f[0]).name for f in findings), ["leak.json", "value.json"])

    def test_scrubber_masks_urls_headers_and_values(self):
        scrub = Scrubber(["sk-test-abcdef123456"])
        self.assertEqual(scrub.headers({"Authorization": "Bearer sk-test-abcdef123456", "x-api-key": "k"})["Authorization"], "[REDACTED]")
        self.assertNotIn("sk-test-abcdef123456", scrub.text("https://x/v1?key=sk-test-abcdef123456 and sk-test-abcdef123456"))


class WorkflowTriggerTests(unittest.TestCase):
    TEXT = (Path(__file__).resolve().parents[4] / ".github" / "workflows" / "llm-probes.yml").read_text()

    def trigger_block(self):
        start = self.TEXT.index("\non:\n") + 1
        return self.TEXT[start:self.TEXT.index("\npermissions:")]

    def test_only_manual_and_workflow_scoped_pull_request(self):
        block = self.trigger_block()
        keys = [line.strip().rstrip(":") for line in block.splitlines() if line.startswith("  ") and not line.startswith("    ")]
        self.assertEqual(sorted(keys), ["pull_request", "workflow_dispatch"])
        paths = [line.strip()[2:] for line in block.split("paths:")[1].split("workflow_dispatch:")[0].splitlines() if "- " in line]
        self.assertEqual(paths, [".github/workflows/llm-probes.yml"])

    def test_jobs_refuse_forks(self):
        self.assertIn("github.event.pull_request.head.repo.full_name == github.repository", self.TEXT)
        self.assertNotIn("pull_request_target", self.TEXT.split("jobs:")[0].replace("There is no pull_request_target", ""))

    def test_every_probe_job_uses_the_environment(self):
        self.assertEqual(self.TEXT.count("environment: llm-probes"), 1)


class RetryTests(unittest.TestCase):
    def test_gateway_error_is_retried_once(self):
        import threading
        from http.server import BaseHTTPRequestHandler, HTTPServer
        from .. import client
        calls = []

        class Flaky(BaseHTTPRequestHandler):
            def do_POST(self):
                calls.append(1)
                self.rfile.read(int(self.headers.get("Content-Length", 0)))
                status = 503 if len(calls) == 1 else 200
                body = b"{}"
                self.send_response(status)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                return None

        server = HTTPServer(("127.0.0.1", 0), Flaky)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        result = client.send("POST", f"http://127.0.0.1:{server.server_address[1]}/x", {}, {"a": 1}, sleep=lambda _: None)
        server.shutdown()
        self.assertEqual((result["status"], result["retries"], len(calls)), (200, 1, 2))


class TimeoutRetryTests(unittest.TestCase):
    def test_timeout_is_not_retried(self):
        import socket
        from .. import client
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(5)
        result = client.send("POST", f"http://127.0.0.1:{listener.getsockname()[1]}/x", {}, {"a": 1}, timeout=1, sleep=lambda _: None)
        listener.close()
        self.assertTrue(result["error"].startswith("TimeoutError"))
        self.assertEqual(result["retries"], 0)


class EffortTests(unittest.TestCase):
    def test_both_capped_is_not_measurable(self):
        from .. import extract
        step = {"json": {"choices": [{"finish_reason": "length"}], "usage": {"completion_tokens": 150}}}
        result = extract.FUNCS["effort_compare"]({"low": step, "high": step}, {"low": "low", "high": "high"}, {})
        self.assertTrue(result["capped"])
        self.assertFalse(result["effective"])


class ReplayTests(unittest.TestCase):
    def test_replay_accepted_when_reasoning_field_is_returned(self):
        _, _, got, _, _ = run_against({"reasoning": "reasoning_content"}, probes="reasoning-replay")
        self.assertEqual(got["reasoning-replay.reasoning-replay"]["outcome"], "compliant")


if __name__ == "__main__":
    unittest.main()
