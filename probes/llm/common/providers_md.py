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
import sys
from pathlib import Path

TIER_TITLES = {"reference": "Reference", "p1": "Priority 1", "p2": "Priority 2", "p3": "Priority 3"}
HEADER = """# Providers for the `{dialect}` dialect

Generated from `providers.json` by `python3 -m common.providers_md`; edit the JSON, not this file.
Docs were read on {read_on}.

How to read this file:

- Every claim carries its source. `official-page` means the provider's own page was fetched when the claim was recorded.
  `search-summary` means it came from a web search result about the provider and was not read from the official page.
  `memory` means it is unconfirmed. Models, base URLs and prices change often: treat anything that is not `official-page`
  as a hypothesis for the live probes to confirm or refute.
- Documented deviations are hypotheses. Every probe runs against every provider regardless, so undocumented differences are
  found too. `expect` is coarse: `compliant`, or non-compliant (`unsupported` / `adjustment-needed`).
  A record's `documented` field is true when the observed outcome agrees with these entries.
- Verification: `verified` = the provider's OpenAI-compatibility or API reference page was read and lists parameters;
  `partial` = some claims confirmed; `unverified` = OpenAI compatibility could not be confirmed from the docs. Unverified
  providers are still probed when a key is available.
- Providers without an API key in the `llm-probes` environment are skipped and listed as skipped in the chart.

## Secrets expected on the `llm-probes` environment

| Provider | Secret | Additional secrets |
|---|---|---|
{secrets}

Local hosts need no secret. Additional secrets are expanded into the base URL. Optional repository settings are not used:
models come from `providers.json`.
"""


def source_note(entry):
    return entry.get("source") or "unspecified"


def provider_block(p):
    price = p.get("price_usd_per_mtok") or {}
    lines = [f"### {p['name']} (`{p['id']}`)", ""]
    lines.append(f"- Tier / hosting: {TIER_TITLES[p['tier']]} / {p['hosting']}")
    lines.append(f"- Secret: `{p['secret']}`" if p.get("secret") else "- Secret: none (local host)")
    lines.append(f"- Base URL: `{p['base_url']}` ({p.get('base_url_source') or 'unspecified'})")
    auth = p.get("auth") or {}
    lines.append(f"- Auth: {auth.get('scheme', 'unknown')} via `{auth.get('header', 'Authorization')}`"
                 + (f" — {auth['notes']}" if auth.get("notes") else ""))
    models = p.get("models") or {}
    lines.append(f"- Models: chat `{models.get('chat')}`, reasoning `{models.get('reasoning')}` ({source_note(models)})"
                 + (f" — {models['notes']}" if models.get("notes") else ""))
    if price.get("input") is not None:
        lines.append(f"- Price (chat model, USD per million tokens): input {price['input']}, output {price['output']} "
                     f"({source_note(price)})")
    else:
        lines.append("- Price: unknown; the runner's fallback price is used for the spend estimate")
    endpoints = p.get("endpoints") or {}
    lines.append(f"- Endpoints: chat completions {endpoints.get('chat_completions')}, responses {endpoints.get('responses')}")
    anthropic = p.get("anthropic_messages_endpoint") or {}
    lines.append(f"- Anthropic Messages endpoint (later phase): offered {anthropic.get('offered')}"
                 + (f", `{anthropic['url']}`" if anthropic.get("url") else "") + f" ({source_note(anthropic)})")
    lines.append(f"- Verification: **{p['verification']}**")
    if p.get("ci_skip"):
        lines.append(f"- Not run in CI: {p['ci_skip']}")
    local = p.get("local")
    if local:
        lines.append(f"- Local host: image `{local.get('image')}`; CPU image available: {local.get('cpu_image_available')}; "
                     f"reasoning fits a CI runner: {local.get('reasoning_fits_ci_runner')} ({local.get('source')})")
    lines.append("- Documented deviations:" + ("" if p["deviations"] else " none recorded"))
    for d in p["deviations"]:
        probes = ", ".join(f"`{i}`" for i in d["probe_ids"]) or "no probe"
        lines.append(f"  - {d['text']} — probes: {probes}; expect: {d['expect']}"
                     + ("; silent" if d.get("silent_expected") else "") + f" ({d['source']})")
    lines.append("- Docs read:")
    for d in p["docs"]:
        lines.append(f"  - {d['url']} — {d['how']}, {d['read_on']}")
    if p.get("notes"):
        lines.append(f"- Notes: {p['notes']}")
    return "\n".join(lines)


def render(data, dialect):
    providers = data["providers"]
    rows = [f"| {p['name']} | {('`' + p['secret'] + '`') if p.get('secret') else 'none'} | "
            f"{', '.join('`' + e + '`' for e in p.get('extra_env', [])) or '—'} |" for p in providers]
    parts = [HEADER.format(dialect=dialect, read_on=data["read_on"], secrets="\n".join(rows))]
    current = None
    for p in providers:
        title = f"{TIER_TITLES[p['tier']]} · {p['hosting']}"
        if title != current:
            parts.append(f"## {title}\n")
            current = title
        parts.append(provider_block(p) + "\n")
    return "\n".join(parts)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--dialect-dir", default=str(Path(__file__).resolve().parents[1] / "openai"))
    args = parser.parse_args(argv)
    directory = Path(args.dialect_dir)
    data = json.loads((directory / "providers.json").read_text())
    (directory / "providers.md").write_text(render(data, directory.name))
    return 0


if __name__ == "__main__":
    sys.exit(main())
