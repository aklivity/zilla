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

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

from . import engine
from .redact import Scrubber

ENV_REF = re.compile(r"\$\{([A-Z0-9_]+)\}")


def expand(text, env):
    missing = [n for n in ENV_REF.findall(text) if not env.get(n)]
    return ENV_REF.sub(lambda m: env.get(m.group(1), ""), text), missing


def load_provider(path, provider_id):
    providers = json.loads(Path(path).read_text())["providers"]
    return next(p for p in providers if p["id"] == provider_id)


def apply_injected(env):
    merged = dict(env)
    for position in ("1", "2", "3"):
        name = env.get(f"PROBE_ENV_NAME_{position}")
        if name:
            merged[name] = env.get(f"PROBE_ENV_VALUE_{position}", "")
    return merged


def run(args, env=os.environ):
    env = apply_injected(env)
    dialect_dir = Path(args.root) / args.dialect
    provider = load_provider(dialect_dir / "providers.json", args.provider)
    meta = {
        "dialect": args.dialect, "provider": provider["id"], "name": provider["name"], "tier": provider["tier"],
        "hosting": provider["hosting"], "zilla_ref": args.zilla_ref, "secret": provider.get("secret"),
        "started": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "skipped": None, "spent_usd": 0.0,
    }
    key = env.get(provider["secret"]) if provider.get("secret") else "unused"
    base_url, missing = expand(provider["base_url"], env)
    records = []
    if args.skip_reason:
        meta["skipped"] = args.skip_reason
    elif not provider["models"].get("chat") and not env.get("LLM_PROBE_CHAT_MODEL"):
        meta["skipped"] = "no chat model configured; set LLM_PROBE_CHAT_MODEL"
    elif not key:
        meta["skipped"] = f"secret {provider['secret']} is not set"
    elif missing:
        meta["skipped"] = f"missing environment: {', '.join(missing)}"
    else:
        extras = [env.get(n, "") for n in provider.get("extra_env", [])]
        scrub = Scrubber([key, *extras])
        models = {
            "chat": env.get("LLM_PROBE_CHAT_MODEL") or provider["models"]["chat"],
            "reasoning": env.get("LLM_PROBE_REASONING_MODEL") or provider["models"].get("reasoning"),
        }
        probes = engine.select(engine.load_probes(dialect_dir / "probes"), args.probes)
        constants = engine.load_constants(dialect_dir / "constants.json")
        budget = engine.Budget(args.budget_usd, provider.get("price_usd_per_mtok"), provider["hosting"] == "local")
        bearer = provider.get("auth", {}).get("scheme", "bearer") == "bearer"
        direct_headers = {"Authorization": f"Bearer {key}"} if bearer else {provider["auth"]["header"]: key}
        targets = []
        if args.via in ("both", "direct"):
            targets.append(engine.Target("direct", base_url, direct_headers))
        if args.via in ("both", "zilla"):
            targets.append(engine.Target("zilla", args.zilla_url, {"Authorization": f"Bearer {key}"}))
        for target in targets:
            ctx = {"constants": constants, "models": models, "zilla_ref": args.zilla_ref,
                   "timeout": args.timeout, "seen_headers": []}
            for probe in probes:
                if target.via == "zilla" and not bearer:
                    probe = dict(probe, zilla={"unsupported": "provider auth is not a Bearer Authorization header"})
                records.append(engine.run_probe(probe, provider, target, ctx, budget, scrub))
        meta["spent_usd"] = round(budget.spent, 6)
    meta["finished"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"meta": meta, "records": records}, indent=2) + "\n")
    return meta, records


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--dialect", default="openai")
    parser.add_argument("--provider", required=True)
    parser.add_argument("--probes", default="all")
    parser.add_argument("--via", choices=["both", "direct", "zilla"], default="both")
    parser.add_argument("--zilla-url", default="http://127.0.0.1:7161/v1")
    parser.add_argument("--zilla-ref", default="develop")
    parser.add_argument("--budget-usd", type=float, default=None)
    parser.add_argument("--timeout", type=int, default=90)
    parser.add_argument("--skip-reason", default=None)
    parser.add_argument("--out", required=True)
    meta, records = run(parser.parse_args(argv))
    counts = {}
    for record in records:
        counts[record["outcome"]] = counts.get(record["outcome"], 0) + 1
    print(f"{meta['provider']}: skipped={meta['skipped']} spent=${meta['spent_usd']} outcomes={counts}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
