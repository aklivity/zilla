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

import base64
import json
import struct
import time
import zlib
from datetime import datetime, timezone
from pathlib import Path

from . import client, extract, jsonpath
from .expr import evaluate, render

OUTCOMES = ("compliant", "adjustment-needed", "unsupported", "not-applicable", "error")
ADJUSTMENTS = ("rename-reshape", "map-sections", "normalise-telemetry", "declare-capability", "handle-unsupported")
FALLBACK_PRICE = {"input": 5.0, "output": 15.0}
MODEL_UNKNOWN = ("not found", "not exist", "unknown model", "invalid model", "no such model", "not supported model")
EXCERPT = 1500
BASELINE_CAP = 1024


class BudgetExhausted(Exception):
    pass


class Budget:
    def __init__(self, limit_usd, price, free):
        self.limit = limit_usd
        self.price = price if price else FALLBACK_PRICE
        self.estimated = not price
        self.free = free
        self.spent = 0.0

    def cost(self, prompt, completion):
        return 0.0 if self.free else (prompt * self.price["input"] + completion * self.price["output"]) / 1_000_000

    def add(self, amount):
        self.spent += amount

    def exhausted(self):
        return self.limit is not None and self.spent >= self.limit


class Target:
    def __init__(self, via, base_url, headers):
        self.via = via
        self.base_url = base_url.rstrip("/")
        self.headers = headers


def red_png():
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    size = 8
    raw = b"".join(b"\x00" + b"\xff\x00\x00" * size for _ in range(size))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    return "data:image/png;base64," + base64.b64encode(png).decode()


def long_prompt():
    words = " ".join(f"entry{n}" for n in range(1, 1201))
    return f"Reference list follows. {words}. Reply with one word: ok."


GENERATORS = {"red_png": red_png, "long_prompt": long_prompt}


def load_constants(path):
    return json.loads(Path(path).read_text()) if Path(path).exists() else {}


def resolve(value, facts, ctx):
    if isinstance(value, dict):
        if "$cap" in value:
            result = {k: resolve(v, facts, ctx) for k, v in value.items() if k != "$cap"}
            result[ctx["cap_param"]] = ctx["cap_override"] or max(value["$cap"], ctx["baseline_cap"])
        elif "$responses_cap" in value:
            result = ctx["responses_cap"] or value["$responses_cap"]
        elif "$ref" in value:
            result = jsonpath.get(facts, value["$ref"])
        elif "$const" in value:
            result = ctx["constants"][value["$const"]]
        elif "$gen" in value:
            result = GENERATORS[value["$gen"]]()
        elif "$model" in value:
            result = ctx["models"][value["$model"]]
        else:
            result = {k: resolve(v, facts, ctx) for k, v in value.items()}
    elif isinstance(value, list):
        result = [resolve(v, facts, ctx) for v in value]
    else:
        result = value
    return result


def load_probes(directory):
    probes = []
    for path in sorted(Path(directory).glob("*.json")):
        area = json.loads(path.read_text())
        for probe in area["probes"]:
            probe["area"], probe["area_id"] = area["area"], area["area_id"]
            probe["full_id"] = f"{area['area_id']}.{probe['id']}"
            probes.append(probe)
    return probes


def select(probes, wanted):
    names = {w.strip() for w in wanted.split(",") if w.strip()} if wanted else set()
    return probes if not names or "all" in names else [
        p for p in probes if p["full_id"] in names or p["area_id"] in names or p["id"] in names]


def _prelude(facts, executed):
    verdict = None
    for step_id in executed:
        step = facts[step_id]
        status = step["status"]
        text = (step.get("text") or "").lower()
        if step["error"]:
            verdict = ("transport error", step["error"])
        elif status in (401, 403):
            verdict = ("authentication rejected", f"HTTP {status}")
        elif status == 429:
            verdict = ("rate limited", f"HTTP 429 after {step.get('retries', 0)} retries")
        elif status is not None and status >= 500:
            verdict = ("server error", f"HTTP {status}: {text[:120]}")
        elif status in (400, 404) and "model" in text and any(m in text for m in MODEL_UNKNOWN) \
                and not facts[step_id].get("expect_model_error"):
            verdict = ("model unavailable", f"HTTP {status}: {text[:120]}")
        if verdict:
            break
    return verdict


def _excerpt(step):
    body = step["text"] if step["text"] else json.dumps(step["stream"]["chunks"][:3]) if step.get("stream") else ""
    return body[:EXCERPT]


def _documented(provider, full_id, outcome, silent, via):
    entries = [d for d in provider.get("deviations", []) if full_id in d.get("probe_ids", [])]
    if via != "direct" or outcome in ("error", "not-applicable"):
        result = None
    elif entries:
        result = any((e["expect"] == "compliant") == (outcome == "compliant") and bool(e.get("silent_expected")) == silent
                     for e in entries)
    elif outcome == "compliant":
        result = True
    elif provider.get("verification") == "verified":
        result = False
    else:
        result = None
    return result


def run_probe(probe, provider, target, ctx, budget, scrub, now=time.time):
    models = ctx["models"]
    record = {
        "provider": provider["id"], "probe": probe["full_id"], "area": probe["area_id"], "via": target.via,
        "status": None, "outcome": "error", "adjustment": None, "silent": False, "documented": None,
        "observed": "", "tokens": {"input": 0, "output": 0}, "cost_usd": 0.0,
        "cost_estimated": budget.estimated and not budget.free,
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "provider_model": models["reasoning" if probe.get("model") == "reasoning" else "chat"],
        "tier": provider["tier"], "zilla_ref": ctx["zilla_ref"], "evidence": [],
    }
    gate = _gate(probe, provider, models)
    if gate:
        record.update(outcome="not-applicable", observed=gate)
    elif budget.exhausted():
        record.update(outcome="error", error_kind="budget", observed=f"budget exhausted at ${budget.spent:.4f}; not run")
    elif target.via == "zilla" and probe.get("zilla", {}).get("unsupported"):
        record.update(outcome="error", error_kind="zilla-inexpressible", observed=probe["zilla"]["unsupported"])
    else:
        _execute(probe, provider, target, ctx, budget, record)
    record["documented"] = _documented(provider, probe["full_id"], record["outcome"], record["silent"], target.via)
    record["observed"] = scrub.text(record["observed"])[:400]
    record["evidence"] = scrub.obj(record["evidence"])
    return record


def _gate(probe, provider, models):
    reason = None
    for need in probe.get("requires", []):
        if need == "reasoning" and not models.get("reasoning"):
            reason = "no reasoning model configured for this provider"
        elif need == "responses" and provider.get("endpoints", {}).get("responses") is False:
            reason = "provider does not offer /responses"
    return reason


def _execute(probe, provider, target, ctx, budget, record):
    facts, executed = {}, []
    ctx_models = dict(ctx["models"])
    ctx_models["model"] = ctx_models["reasoning" if probe.get("model") == "reasoning" else "chat"]
    local = {"constants": ctx["constants"], "models": ctx_models, "cap_param": provider.get("cap_param", "max_tokens"),
             "baseline_cap": provider.get("baseline_cap", BASELINE_CAP),
             "responses_cap": provider.get("responses_cap"),
             "cap_override": provider.get("reasoning_cap") if probe.get("model") == "reasoning" else None}
    derive_ctx = {"constants": ctx["constants"], "models": ctx_models, "seen_headers": ctx["seen_headers"]}
    prompt_tokens = completion_tokens = 0
    for step in probe["steps"]:
        if "when" in step and not evaluate(step["when"], facts):
            facts[step["id"]] = {"skipped": True, "status": None}
            continue
        body = resolve(step.get("body"), facts, local)
        if probe.get("model") != "reasoning" and isinstance(body, dict) and "messages" in body:
            body = {**provider.get("baseline_params", {}), **body}
        result = client.send(step.get("method", "POST"), target.base_url + step["path"], target.headers, body,
                             stream=bool(isinstance(body, dict) and body.get("stream")), timeout=ctx["timeout"])
        result["expect_model_error"] = step.get("expect_model_error", False)
        facts[step["id"]] = result
        executed.append(step["id"])
        ctx["seen_headers"].append(result["headers"])
        record["retries"] = record.get("retries", 0) + result.get("retries", 0)
        prompt, completion = extract.usage_tokens(result)
        prompt_tokens += prompt
        completion_tokens += completion
        budget.add(budget.cost(prompt, completion))
        record["evidence"].append({
            "step": step["id"], "request": {"method": step.get("method", "POST"), "path": step["path"], "body": body},
            "status": result["status"], "elapsed_ms": result["elapsed_ms"], "response": _excerpt(result),
            "error": result["error"]})
        for item in probe.get("derive", []):
            if item.get("after") == step["id"]:
                _derive(item, facts, derive_ctx)
        if step.get("pause"):
            time.sleep(step["pause"])
    record["tokens"] = {"input": prompt_tokens, "output": completion_tokens}
    record["elapsed_ms"] = sum(e["elapsed_ms"] for e in record["evidence"])
    record["cost_usd"] = round(budget.cost(prompt_tokens, completion_tokens), 6)
    record["status"] = next((facts[s]["status"] for s in executed), None)
    verdict = _prelude(facts, executed)
    if verdict:
        record.update(outcome="error", error_kind=verdict[0], observed=f"{verdict[0]}: {verdict[1]}")
    else:
        _classify(probe, facts, derive_ctx, record)


def _derive(item, facts, derive_ctx):
    try:
        facts[item["id"]] = extract.FUNCS[item["fn"]](facts, item.get("args", {}), derive_ctx)
    except (KeyError, TypeError, ValueError, AttributeError) as exc:
        facts[item["id"]] = {"derive_error": f"{type(exc).__name__}: {exc}"}


def _classify(probe, facts, derive_ctx, record):
    for item in probe.get("derive", []):
        if "after" not in item:
            _derive(item, facts, derive_ctx)
    for rule in probe["classify"]:
        if evaluate(rule["when"], facts):
            record.update(outcome=rule["outcome"], adjustment=rule.get("adjustment"), silent=bool(rule.get("silent")),
                          observed=render(rule["observed"], facts))
            break
    else:
        record.update(outcome="error", error_kind="unclassified",
                      observed=f"no rule matched; status={record['status']}")
