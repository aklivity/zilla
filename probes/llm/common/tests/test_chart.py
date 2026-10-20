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

import copy
import json
import tempfile
import unittest
from pathlib import Path

from .. import chart, zilla_config
from .harness import REAL, run_against


def results(profile, provider_id, tier, hosting, via_zilla_outcomes=None):
    meta, records, _, _, _ = run_against(profile, {"id": provider_id, "name": provider_id.title(), "tier": tier,
                                                  "hosting": hosting}, probes="roles,ranges")
    meta = dict(meta, provider=provider_id, name=provider_id.title(), tier=tier, hosting=hosting)
    for r in records:
        r["provider"], r["tier"] = provider_id, tier
    extra = []
    for r in records:
        z = copy.deepcopy(r)
        z["via"] = "zilla"
        if via_zilla_outcomes and r["probe"] in via_zilla_outcomes:
            z["outcome"] = via_zilla_outcomes[r["probe"]]
        extra.append(z)
    return {"meta": meta, "records": records + extra}


class ChartTests(unittest.TestCase):
    def render(self, previous=None):
        providers = {p["id"]: p for p in json.loads((REAL / "providers.json").read_text())["providers"]}
        ref = results({}, "openai", "reference", "hosted")
        quirk = results({"reject_name": True, "ignore_n": True}, "groq", "p1", "hosted", {"roles.name-field": "compliant"})
        fp = results({"reject_developer": True}, "gemini-openai", "p2", "first-party-compat")
        skipped = {"meta": dict(ref["meta"], provider="deepseek", name="DeepSeek", tier="p1", skipped="secret DEEPSEEK_API_KEY is not set"),
                   "records": []}
        files = [ref, quirk, fp, skipped]
        with tempfile.TemporaryDirectory() as tmp:
            for data in files:
                (Path(tmp) / f"{data['meta']['provider']}.json").write_text(json.dumps(data))
            metas, records = chart.load(tmp)
        areas = chart.areas_of(REAL / "probes")
        return chart.render(metas, records, previous, providers, areas, "2.6.0-rc1"), records

    def test_matrix_symbols(self):
        text, _ = self.render()
        rows = {line.split("|")[1].strip(): line for line in text.splitlines()
                if line.startswith("| ") and any(symbol in line for symbol in ("✅", "⚠️", "❌"))}
        self.assertIn("Openai", rows)
        self.assertNotIn("🔀", rows["Openai"])
        self.assertIn("🔀", rows["Groq"])
        self.assertIn("🤫", rows["Groq"])
        self.assertIn("⚠️", rows["Gemini-Openai"])
        self.assertLess(text.index("Reference"), text.index("Priority 1"))
        self.assertIn("DeepSeek: secret DEEPSEEK_API_KEY is not set", text)

    def test_sections(self):
        text, records = self.render()
        for heading in ("Matrix", "Changed since last run", "Silent behaviours", "Documented vs observed", "First-party comparison",
                        "Adjustments needed", "Unsupported and errors", "Anthropic-compatible endpoints noted", "Run metadata"):
            self.assertIn(f"## {heading}", text)
        self.assertIn("| groq | ranges.n-2 | direct |", text)
        self.assertIn("**Map sections**", text)
        failures = text.split("## Unsupported and errors")[1].split("## Anthropic")[0]
        self.assertIn("| groq | ranges.n-2 | direct | unsupported |", failures)
        self.assertIn("roles.developer-role", text.split("## First-party comparison")[1].split("## Adjustments")[0])

    def test_changes_since_previous(self):
        _, records = self.render()
        previous = copy.deepcopy(records)
        for r in previous:
            if r["provider"] == "groq" and r["probe"] == "roles.name-field" and r["via"] == "direct":
                r["outcome"] = "compliant"
            if r["provider"] == "groq" and r["probe"] == "ranges.n-2" and r["via"] == "direct":
                r["outcome"] = "unsupported"
                r["silent"] = False
        text, _ = self.render(previous)
        section = text.split("## Changed since last run")[1].split("## Silent")[0]
        self.assertIn("**Newly broken**", section)
        self.assertRegex(section, r"groq \| roles.name-field \| direct \| compliant \| adjustment-needed")


class ZillaConfigTests(unittest.TestCase):
    def test_generated_config(self):
        text = zilla_config.build("openai", "https://api.groq.com/openai/v1")
        self.assertIn("server: https://api.groq.com:443/openai/v1", text)
        self.assertIn('credentials: "Bearer {credentials}"', text)
        self.assertNotIn("sk-", text)

    def test_rejects_unexpected_characters(self):
        with self.assertRaises(ValueError):
            zilla_config.build("openai", "https://x.example/v1\n  injected: true")


if __name__ == "__main__":
    unittest.main()
