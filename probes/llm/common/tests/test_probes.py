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
import unittest
from pathlib import Path

from .. import engine, schemacheck
from .harness import REAL, run_against

SCHEMA = Path(__file__).resolve().parents[1] / "schema.json"


def outcomes(by_probe):
    return {k: (r["outcome"], r["adjustment"], r["silent"]) for k, r in by_probe.items()}


class ProbeFiles(unittest.TestCase):
    def test_declarations_are_well_formed(self):
        probes = engine.load_probes(REAL / "probes")
        ids = [p["full_id"] for p in probes]
        self.assertEqual(len(ids), len(set(ids)))
        for probe in probes:
            self.assertTrue(probe["classify"], probe["full_id"])
            for rule in probe["classify"]:
                self.assertIn(rule["outcome"], engine.OUTCOMES)
                if rule.get("adjustment"):
                    self.assertEqual(rule["outcome"], "adjustment-needed", probe["full_id"])
                    self.assertIn(rule["adjustment"], engine.ADJUSTMENTS)
                if rule["outcome"] == "adjustment-needed":
                    self.assertIn("adjustment", rule, probe["full_id"])
                self.assertTrue(rule["observed"])

    def test_providers_reference_known_probes(self):
        known = {p["full_id"] for p in engine.load_probes(REAL / "probes")}
        providers = json.loads((REAL / "providers.json").read_text())["providers"]
        self.assertEqual(providers[0]["id"], "openai")
        for provider in providers:
            for key in ("id", "name", "tier", "hosting", "base_url", "models", "deviations", "docs", "verification"):
                self.assertIn(key, provider)
            for deviation in provider["deviations"]:
                self.assertTrue(set(deviation["probe_ids"]) <= known, provider["id"])
            if provider["hosting"] != "local":
                self.assertRegex(provider["secret"], r"^[A-Z0-9_]+_API_KEY$")


class CompliantProvider(unittest.TestCase):
    def test_every_probe_is_compliant(self):
        meta, records, by_probe, text, mock = run_against({})
        expected = len(engine.load_probes(REAL / "probes"))
        self.assertEqual(len(by_probe), expected)
        bad = {k: (r["outcome"], r["observed"]) for k, r in by_probe.items() if r["outcome"] != "compliant"}
        self.assertEqual(list(bad), ["reasoning-replay.reasoning-replay"])
        self.assertEqual(bad["reasoning-replay.reasoning-replay"][0], "not-applicable")
        schema = json.loads(SCHEMA.read_text())
        for record in records:
            self.assertEqual(schemacheck.validate(record, schema["$defs"]["record"]), [], record["probe"])
        self.assertGreater(meta["spent_usd"], 0)

    def test_key_is_never_written(self):
        secret = "sk-test-secret-0123456789"
        meta, records, by_probe, text, mock = run_against({}, probes="roles")
        self.assertNotIn(secret, text)
        self.assertNotIn(secret, json.dumps(mock.state.requests[0][2]))
        sent = mock.state.requests[0][1]
        self.assertEqual(sent["Authorization"], f"Bearer {secret}")


class QuirkyProvider(unittest.TestCase):
    def test_quirks_are_classified(self):
        profile = {
            "reject_max_completion_tokens": True, "reject_developer": True, "reject_name": True, "reject_n": True,
            "ignore_logprobs": True, "stop_limit": 4, "reasoning": "reasoning_content", "usage": "vendor",
            "cached": "vendor", "error_shape": "detail", "model_echo": "prefixed", "rate_headers": "vendor",
            "tool_single_chunk": True, "reject_parallel": True, "responses": False, "ignore_effort": True,
            "reject_replay": True,
        }
        _, _, got, _, _ = run_against(profile)
        o = outcomes(got)
        self.assertEqual(o["output-cap.max-completion-tokens"], ("adjustment-needed", "rename-reshape", False))
        self.assertEqual(o["roles.developer-role"], ("adjustment-needed", "map-sections", False))
        self.assertEqual(o["roles.name-field"], ("adjustment-needed", "handle-unsupported", False))
        self.assertEqual(o["ranges.n-2"][0], "unsupported")
        self.assertEqual(o["ranges.logprobs"], ("unsupported", None, True))
        self.assertEqual(o["ranges.stop-5"], ("adjustment-needed", "handle-unsupported", False))
        self.assertEqual(o["reasoning.reasoning-shape"], ("adjustment-needed", "map-sections", False))
        self.assertEqual(o["reasoning.reasoning-effort"], ("unsupported", None, True))
        self.assertEqual(o["reasoning-replay.reasoning-replay"], ("adjustment-needed", "map-sections", False))
        self.assertEqual(o["streaming-usage.usage-included"], ("adjustment-needed", "normalise-telemetry", False))
        self.assertEqual(o["cached-tokens.cached-tokens"], ("adjustment-needed", "normalise-telemetry", False))
        self.assertEqual(o["errors.error-shape"], ("adjustment-needed", "rename-reshape", False))
        self.assertEqual(o["errors.rate-limit-headers"], ("adjustment-needed", "normalise-telemetry", False))
        self.assertEqual(o["model-identity.model-identity"], ("adjustment-needed", "normalise-telemetry", False))
        self.assertEqual(o["tool-streaming.tool-stream"], ("adjustment-needed", "rename-reshape", False))
        self.assertEqual(o["tools.tool-required"], ("adjustment-needed", "handle-unsupported", False))
        self.assertEqual(o["responses.responses-basic"][0], "unsupported")

    def test_silent_parameters(self):
        _, _, got, _, _ = run_against({"ignore_cap": True, "ignore_n": True, "ignore_seed": True, "ignore_developer": True,
                                       "no_images": True, "usage": "none", "cached": "none", "store_false": True})
        o = outcomes(got)
        self.assertEqual(o["output-cap.max-completion-tokens"], ("unsupported", None, True))
        self.assertEqual(o["ranges.n-2"], ("unsupported", None, True))
        self.assertEqual(o["roles.developer-role"], ("adjustment-needed", "map-sections", True))
        self.assertEqual(o["images.image-part"], ("unsupported", None, True))
        self.assertEqual(o["streaming-usage.usage-included"], ("unsupported", None, True))
        self.assertEqual(o["cached-tokens.cached-tokens"][0], "unsupported")
        self.assertEqual(o["responses.responses-store"], ("unsupported", None, True))

    def test_gating_and_documentation(self):
        overrides = {"models": {"chat": "mock-chat", "reasoning": None}, "endpoints": {"responses": False},
                     "deviations": [{"probe_ids": ["roles.name-field"], "expect": "unsupported", "silent_expected": False}]}
        _, _, got, _, _ = run_against({"reject_name": True}, overrides)
        self.assertEqual(got["reasoning.reasoning-shape"]["outcome"], "not-applicable")
        self.assertEqual(got["responses.responses-basic"]["outcome"], "not-applicable")
        self.assertTrue(got["roles.name-field"]["documented"])
        self.assertTrue(got["output-cap.max-tokens"]["documented"])

    def test_errors_are_recorded_not_raised(self):
        _, _, got, _, _ = run_against({}, {"models": {"chat": "wrong-model", "reasoning": None}}, probes="roles")
        self.assertTrue(all(r["outcome"] == "error" for r in got.values()))
        self.assertIn("model unavailable", got["roles.name-field"]["observed"])

    def test_model_not_exist_wording_is_an_error(self):
        step = {"status": 400, "error": None, "text": '{"error":{"message":"Model Not Exist"}}'}
        self.assertEqual(engine._prelude({"a": step}, ["a"])[0], "model unavailable")

    def test_zilla_inexpressible_probe(self):
        _, records, _, _, _ = run_against({}, probes="responses", via="zilla")
        self.assertTrue(all(r["outcome"] == "error" and r["error_kind"] == "zilla-inexpressible" for r in records))


class ProviderSettings(unittest.TestCase):
    def test_baseline_params_and_reasoning_cap_apply(self):
        overrides = {"baseline_params": {"chat_template_kwargs": {"enable_thinking": False}}, "reasoning_cap": 400}
        _, _, _, _, mock = run_against({}, overrides, probes="roles.name-field,reasoning.reasoning-shape")
        baseline = next(r for _, _, r in mock.state.requests if r["model"] == "mock-chat")
        reasoning = next(r for _, _, r in mock.state.requests if r["model"] == "mock-reason")
        self.assertEqual(baseline["chat_template_kwargs"], {"enable_thinking": False})
        self.assertNotIn("chat_template_kwargs", reasoning)
        self.assertEqual(reasoning["max_tokens"], 400)
        self.assertEqual(baseline["max_tokens"] if "max_tokens" in baseline else 1024, 1024)


class BudgetTests(unittest.TestCase):
    def test_budget_stops_probing(self):
        budget = engine.Budget(0.0, {"input": 1.0, "output": 1.0}, False)
        self.assertFalse(budget.exhausted() and False)
        budget.add(0.01)
        self.assertTrue(budget.exhausted())


if __name__ == "__main__":
    unittest.main()
