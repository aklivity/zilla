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
from pathlib import Path

from . import runner

TIERS = {"p1", "p2", "p3"}
TOKEN = re.compile(r"^[a-z0-9][a-z0-9\-]*$")
PROBES = re.compile(r"^[a-z0-9.,\-]+$")


def expand(selection, providers, dialect_tiers=TIERS):
    names = [s.strip() for s in selection.split(",") if s.strip()]
    known = {p["id"]: p for p in providers}
    chosen = []
    for name in names:
        if name == "all":
            chosen.extend(p["id"] for p in providers)
        elif name in dialect_tiers:
            chosen.extend(p["id"] for p in providers if p["tier"] == name)
        elif TOKEN.match(name) and name in known:
            chosen.append(name)
        else:
            raise ValueError(f"unknown provider or tier: {name}")
    wanted_reference = any(known[c]["hosting"] == "first-party-compat" or c == "openai" for c in chosen) or \
        any(n in ("all", *dialect_tiers) for n in names)
    if wanted_reference and "openai" in known:
        chosen.insert(0, "openai")
    return [known[c] for c in dict.fromkeys(chosen)]


def entry(provider, share):
    extras = provider.get("extra_env", [])
    return {
        "id": provider["id"], "name": provider["name"], "tier": provider["tier"], "hosting": provider["hosting"],
        "secret": provider.get("secret") or "", "extra_secret_1": extras[0] if extras else "",
        "extra_secret_2": extras[1] if len(extras) > 1 else "", "local": provider.get("ci_start", ""),
        "budget_usd": share,
    }


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--results", required=True)
    args = parser.parse_args(argv)
    dialect, selection = os.environ["DIALECT"], os.environ["PROVIDERS"]
    probes, via, budget = os.environ["PROBES"], os.environ["VIA"], float(os.environ["BUDGET_USD"])
    if not TOKEN.match(dialect) or not (Path(args.root) / dialect / "providers.json").exists():
        raise ValueError(f"unsupported dialect: {dialect}")
    if not PROBES.match(probes) or via not in ("both", "direct", "zilla") or budget <= 0:
        raise ValueError("invalid probes, via or budget_usd input")
    providers = json.loads((Path(args.root) / dialect / "providers.json").read_text())["providers"]
    chosen = expand(selection, providers)
    paid = [p for p in chosen if p["hosting"] != "local" and not p.get("ci_skip")]
    share = round(budget / max(1, len(paid)), 4)
    matrix, skipped = [], []
    for provider in chosen:
        if provider.get("ci_skip"):
            runner.main(["--root", args.root, "--dialect", dialect, "--provider", provider["id"], "--via", via,
                         "--skip-reason", provider["ci_skip"], "--out", f"{args.results}/{provider['id']}.json"])
            skipped.append(provider["id"])
        else:
            matrix.append(entry(provider, share if provider["hosting"] != "local" else budget))
    with open(os.environ["GITHUB_OUTPUT"], "a") as out:
        out.write(f"matrix={json.dumps({'include': matrix})}\n")
        out.write(f"count={len(matrix)}\n")
    print(f"selected {len(matrix)} providers, {len(skipped)} skipped by catalogue; budget share ${share} each")
    return 0


if __name__ == "__main__":
    sys.exit(main())
