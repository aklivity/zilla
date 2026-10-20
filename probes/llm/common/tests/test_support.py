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


class ReplayTests(unittest.TestCase):
    def test_replay_accepted_when_reasoning_field_is_returned(self):
        _, _, got, _, _ = run_against({"reasoning": "reasoning_content"}, probes="reasoning-replay")
        self.assertEqual(got["reasoning-replay.reasoning-replay"]["outcome"], "compliant")


if __name__ == "__main__":
    unittest.main()
