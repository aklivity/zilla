# LLM compatibility probes

Live, behavioural probes that measure how closely OpenAI-compatible providers follow the OpenAI API, and whether Zilla's
`llm(server)` → `llm(client)` pair with the `openai` dialect (endpoint and credentials only) passes each behaviour through.
Each probe is sent twice: directly to the provider, and through a Zilla container started from the image for `zilla_ref`.

Setup (environment, keys) and targeted runs: see [SETUP.md](SETUP.md).

Run it from the Actions tab: **llm-probes** (`workflow_dispatch` only). The chart is written to the run summary and uploaded,
with the merged JSON, as the `llm-probes-merged` artifact.

## Layout

```
probes/llm/
  common/                 dialect-neutral code
    engine.py             executes a probe's steps, classifies the result
    extract.py            small named measurements (where is usage, where is reasoning, ...)
    runner.py             one provider, direct and/or through Zilla -> results/<provider>.json
    setup.py              expands workflow inputs into the provider matrix
    zilla_config.py       generates the llm(server) -> llm(client) zilla.yaml for a provider
    chart.py              renders COMPATIBILITY.md and the merged JSON
    schema.json           result schema (one record per provider, probe, via)
    redact.py, scan_secrets.py   credential redaction and the pre-upload scan
    local_host.sh         starts a local host container (ollama, llama.cpp, vLLM)
    tests/                unit tests and a mock OpenAI-compatible provider
  openai/                 the `openai` dialect
    providers.json        provider catalogue (source of truth); providers.md is generated from it
    constants.json        fixtures the probes reference
    probes/*.json         one file per probe area
```

`anthropic/` and `gemini/` directories will sit next to `openai/` with the same three pieces.

## Probes are data

A probe file lists `steps` (HTTP requests; bodies may use `{"$model": ...}`, `{"$cap": n}`, `{"$const": ...}`,
`{"$gen": ...}` and `{"$ref": "<step>.json.path"}`), optional `derive` entries (named measurements from `extract.py`) and an
ordered `classify` list. The first rule whose `when` condition holds sets the `outcome`, the `adjustment`, `silent` and the
`observed` text. Transport errors, 401/403, 429 and 5xx are classified `error` before the rules run.

Outcomes: `compliant`, `adjustment-needed` (with one of `rename-reshape`, `map-sections`, `normalise-telemetry`,
`declare-capability`, `handle-unsupported`), `unsupported`, `not-applicable`, `error`.

Python (standard library only) was chosen for the runner: it ships on every runner image, needs no install step, and
handles the streaming (SSE) analysis the probes need.

Per-provider settings in `providers.json` that shape the requests: `cap_param` (`max_tokens` or `max_completion_tokens`),
`baseline_cap` (floor for the output cap of non-capping probes, default 1024, so models that think by default can answer),
`baseline_params` (extra body fields for every non-reasoning probe, used to turn thinking off on local hosts) and
`reasoning_cap` (output cap for the reasoning probes).

## Running locally

```bash
cd probes/llm
python3 -m unittest discover -s common/tests -t .             # unit tests against the mock provider
export GROQ_API_KEY=...                                       # never commit or echo keys
python3 -m common.runner --provider groq --via direct --out /tmp/groq.json
python3 -m common.providers_md                                # regenerate providers.md
```

For `--via zilla`, start Zilla with `python3 -m common.zilla_config --dialect openai --provider groq > etc/zilla.yaml`
and the image of your choice on port 7161.

## Safety

- Provider keys are environment secrets on the `llm-probes` GitHub environment, named `<PROVIDER>_API_KEY`
  (see `openai/providers.md`). Each matrix job receives only its own provider's secrets, in the step that needs them.
- Credentials are never written to `zilla.yaml`: the caller's own bearer token is passed through Zilla.
- Recorded requests are redacted by header name, by the exact secret values and by common token patterns; the workflow runs
  `scan_secrets` before every upload and fails the job on a hit.
- The workflow runs on `workflow_dispatch`, and on pull requests from this repository only when the workflow file itself
  changes (a one-request smoke run). It has no `pull_request_target`, `push` or `schedule` trigger, and its jobs refuse
  pull requests from forks. A unit test pins this.
