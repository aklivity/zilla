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
import sys
from datetime import datetime, timezone
from pathlib import Path

from . import engine

SYMBOL = {"compliant": "✅", "adjustment-needed": "⚠️", "unsupported": "❌", "not-applicable": "—", "error": "❓"}
ADJUSTMENT_TITLES = {
    "rename-reshape": "Rename / reshape",
    "map-sections": "Map sections",
    "normalise-telemetry": "Normalise telemetry",
    "declare-capability": "Declare capability",
    "handle-unsupported": "Handle unsupported",
}
GROUPS = [
    ("reference", None, "Reference"),
    ("p1", "hosted", "Priority 1 · hosted"),
    ("p1", "local", "Priority 1 · local"),
    ("p2", "hosted", "Priority 2 · hosted"),
    ("p2", "local", "Priority 2 · local"),
    ("p2", "first-party-compat", "Priority 2 · first-party OpenAI-compatible"),
    ("p3", "hosted", "Priority 3 · hosted"),
    ("p3", "gateway", "Priority 3 · gateways"),
]
SEVERITY = ["unsupported", "adjustment-needed", "error", "compliant"]


def cell_outcome(records):
    present = [r["outcome"] for r in records]
    result = "not-applicable"
    for candidate in SEVERITY:
        if candidate in present:
            result = candidate
            break
    return result


def group_key(meta):
    hosting = "hosted" if meta["hosting"] == "gateway" and meta["tier"] != "p3" else meta["hosting"]
    return (meta["tier"], None if meta["tier"] == "reference" else hosting)


def esc(text):
    return str(text).replace("|", "\\|").replace("\n", " ")


def load(results_dir):
    metas, records = [], []
    for path in sorted(Path(results_dir).glob("*.json")):
        data = json.loads(path.read_text())
        if "meta" in data:
            metas.append(data["meta"])
            records.extend(data["records"])
    return metas, records


def index(records):
    return {(r["provider"], r["probe"], r["via"]): r for r in records}


def matrix(metas, records, areas):
    by = {}
    for r in records:
        by.setdefault((r["provider"], r["area"], r["via"]), []).append(r)
    header = "| Provider | " + " | ".join(name for _, name in areas) + " |"
    lines = [header, "|---|" + "---|" * len(areas)]
    ran = [m for m in metas if not m["skipped"]]
    for tier, hosting, title in GROUPS:
        members = [m for m in ran if group_key(m) == (tier, hosting)]
        if members:
            lines.append(f"| **{title}** |" + " |" * len(areas))
        for meta in sorted(members, key=lambda m: m["provider"]):
            cells = []
            for area_id, _ in areas:
                direct = by.get((meta["provider"], area_id, "direct"), [])
                zilla = by.get((meta["provider"], area_id, "zilla"), [])
                text = SYMBOL[cell_outcome(direct or zilla)]
                if direct and zilla and diverges(direct, zilla):
                    text += "🔀"
                if any(r["silent"] for r in direct + zilla):
                    text += "🤫"
                cells.append(text)
            lines.append(f"| {esc(meta['name'])} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def diverges(direct, zilla):
    left = {r["probe"]: (r["outcome"], r["silent"]) for r in direct}
    right = {r["probe"]: (r["outcome"], r["silent"]) for r in zilla}
    return any(left[p] != right[p] for p in left.keys() & right.keys())


def changed(records, previous):
    lines = []
    if previous is None:
        lines.append("No previous successful run results were available to compare.")
    else:
        old = index(previous)
        newly_ok, newly_broken = [], []
        for key, record in sorted(index(records).items()):
            before = old.get(key)
            if before and before["outcome"] != record["outcome"]:
                row = f"| {key[0]} | {key[1]} | {key[2]} | {before['outcome']} | {record['outcome']} | {esc(record['observed'])} |"
                if record["outcome"] == "compliant":
                    newly_ok.append(row)
                elif before["outcome"] == "compliant":
                    newly_broken.append(row)
        head = "| Provider | Probe | Via | Before | Now | Observed |\n|---|---|---|---|---|---|"
        for title, rows in (("Newly compliant", newly_ok), ("Newly broken", newly_broken)):
            lines.append(f"**{title}**\n")
            lines.append(head + "\n" + "\n".join(rows) if rows else "None.")
            lines.append("")
    return "\n".join(lines)


def silent_section(records):
    rows = [f"| {r['provider']} | {r['probe']} | {r['via']} | {esc(r['observed'])} |"
            for r in sorted(records, key=lambda r: (r["provider"], r["probe"], r["via"])) if r["silent"]]
    return "| Provider | Probe | Via | Observed |\n|---|---|---|---|\n" + "\n".join(rows) if rows else "None."


def documented_section(records, providers):
    rows = []
    for r in sorted(records, key=lambda r: (r["provider"], r["probe"])):
        if r["via"] == "direct" and r["documented"] is False:
            docs = [d["text"] for d in providers.get(r["provider"], {}).get("deviations", []) if r["probe"] in d["probe_ids"]]
            rows.append(f"| {r['provider']} | {r['probe']} | {esc(docs[0]) if docs else 'no documented deviation'} | "
                        f"{r['outcome']}: {esc(r['observed'])} |")
    return "| Provider | Probe | Documentation | Observed |\n|---|---|---|---|\n" + "\n".join(rows) if rows else "None."


def first_party_section(metas, records):
    base = {r["probe"]: r for r in records if r["provider"] == "openai" and r["via"] == "direct"}
    parts = []
    for meta in sorted(metas, key=lambda m: m["provider"]):
        if meta["hosting"] != "first-party-compat" or meta["skipped"]:
            continue
        rows = []
        for r in sorted(records, key=lambda r: r["probe"]):
            ref = base.get(r["probe"])
            if r["provider"] == meta["provider"] and r["via"] == "direct" and r["outcome"] in ("unsupported", "adjustment-needed"):
                if ref is None or ref["outcome"] == "compliant":
                    rows.append(f"| {r['probe']} | {r['outcome']} | {esc(r['observed'])} |")
        parts.append(f"**{meta['name']}**\n\n" + ("| Probe | Outcome | Observed |\n|---|---|---|\n" + "\n".join(rows) if rows
                                                    else "No differences from the reference in this run."))
    return "\n\n".join(parts) if parts else "No first-party OpenAI-compatible endpoint ran."


def adjustments_section(records):
    parts = []
    for kind in engine.ADJUSTMENTS:
        rows = [f"| {r['provider']} | {r['probe']} | {esc(r['observed'])} |"
                for r in sorted(records, key=lambda r: (r["provider"], r["probe"]))
                if r["via"] == "direct" and r["outcome"] == "adjustment-needed" and r["adjustment"] == kind]
        parts.append(f"**{ADJUSTMENT_TITLES[kind]}**\n\n" + ("| Provider | Probe | Observed |\n|---|---|---|\n" + "\n".join(rows)
                                                            if rows else "None."))
    return "\n\n".join(parts)


def failures_section(records):
    rows = [f"| {r['provider']} | {r['probe']} | {r['via']} | {r['outcome']} | {esc(r['observed'])} |"
            for r in sorted(records, key=lambda r: (r["provider"], r["probe"], r["via"]))
            if r["outcome"] in ("unsupported", "error")]
    return ("| Provider | Probe | Via | Outcome | Observed |\n|---|---|---|---|---|\n" + "\n".join(rows)) if rows else "None."


def anthropic_section(providers):
    rows = [f"| {p['name']} | {p['anthropic_messages_endpoint'].get('url') or 'n/a'} | {p['anthropic_messages_endpoint'].get('source')} |"
            for p in providers.values()
            if (p.get("anthropic_messages_endpoint") or {}).get("offered")]
    return "| Provider | Endpoint | Source |\n|---|---|---|\n" + "\n".join(rows) if rows else "None recorded."


def metadata_section(metas, records, providers, zilla_ref, run_url):
    spent = sum(m["spent_usd"] for m in metas)
    estimated = any(r["cost_estimated"] for r in records)
    skipped = [f"{m['name']}: {m['skipped']}" for m in sorted(metas, key=lambda m: m["provider"]) if m["skipped"]]
    unselected = sorted(p["name"] for pid, p in providers.items() if pid not in {m["provider"] for m in metas})
    lines = [
        f"- Zilla ref: {zilla_ref}",
        f"- Run date: {datetime.now(timezone.utc).strftime('%Y-%m-%d')}",
        f"- Estimated spend: ${spent:.4f}" + (" (includes fallback prices for providers with unknown pricing)" if estimated else ""),
        f"- Providers run: {sum(1 for m in metas if not m['skipped'])}",
        "- Providers skipped:" + ("" if skipped else " none"),
        *[f"  - {s}" for s in skipped],
        "- Providers not selected for this run:" + (" " + ", ".join(unselected) if unselected else " none"),
    ]
    if run_url:
        lines.append(f"- Run: {run_url}")
    return "\n".join(lines)


def render(metas, records, previous, providers, areas, zilla_ref, run_url=None):
    sections = [
        ("# LLM compatibility: `openai` dialect", ""),
        ("", "✅ compliant · ⚠️ adjustment needed · ❌ unsupported · ❓ error / not run · — not applicable · "
             "🔀 direct and through-Zilla results differ · 🤫 HTTP 200 but parameter not honoured"),
        ("## Matrix", matrix(metas, records, areas)),
        ("## Changed since last run", changed(records, previous)),
        ("## Silent behaviours", silent_section(records)),
        ("## Documented vs observed", documented_section(records, providers)),
        ("## First-party comparison", first_party_section(metas, records)),
        ("## Adjustments needed", adjustments_section(records)),
        ("## Unsupported and errors", failures_section(records)),
        ("## Anthropic-compatible endpoints noted", anthropic_section(providers)),
        ("## Run metadata", metadata_section(metas, records, providers, zilla_ref, run_url)),
    ]
    return "\n\n".join(f"{h}\n\n{b}".strip() if h else b for h, b in sections) + "\n"


def areas_of(probes_dir):
    seen = {}
    for probe in engine.load_probes(probes_dir):
        seen.setdefault(probe["area_id"], probe["area"])
    return list(seen.items())


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True)
    parser.add_argument("--previous", default=None)
    parser.add_argument("--dialect-dir", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--merged", required=True)
    parser.add_argument("--zilla-ref", default="develop")
    args = parser.parse_args(argv)
    metas, records = load(args.results)
    previous = None
    if args.previous and Path(args.previous).exists():
        previous = json.loads(Path(args.previous).read_text()).get("records")
    dialect_dir = Path(args.dialect_dir)
    providers = {p["id"]: p for p in json.loads((dialect_dir / "providers.json").read_text())["providers"]}
    run_url = None
    if os.environ.get("GITHUB_RUN_ID"):
        run_url = f"{os.environ.get('GITHUB_SERVER_URL')}/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    Path(args.out).write_text(render(metas, records, previous, providers, areas_of(dialect_dir / "probes"), args.zilla_ref, run_url))
    Path(args.merged).write_text(json.dumps({"zilla_ref": args.zilla_ref, "metas": metas, "records": records}, indent=1) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
