# Setting up and running the probes

## 1. Create the `llm-probes` environment

You need admin access to `aklivity/zilla`.

1. Repository **Settings → Environments → New environment**, name it exactly `llm-probes`.
2. Under **Deployment protection rules**, enable **Required reviewers** and add the people who may approve spending.
   Every run then pauses until a reviewer approves it on the run page (**Review deployments**). Approving the environment once should release
   all the provider jobs of that run (confirm on your first run).
3. Under **Deployment branches and tags**, choose **Selected branches and tags** and allow the branches the workflow
   may run from (for example `develop` and the working branch). Environment secrets are then unavailable to any other ref.
4. Add each key under **Environment secrets** (not repository secrets, so only jobs that name the environment can read them).

The same with the GitHub CLI (it prompts for each value, so nothing lands in shell history):

```bash
gh api -X PUT repos/aklivity/zilla/environments/llm-probes
gh secret set GROQ_API_KEY --env llm-probes --repo aklivity/zilla
```

## 2. Which keys

Secret names are fixed: `<PROVIDER>_API_KEY`. A provider without its key is skipped and listed under "Providers skipped"
in the chart. Local hosts (llama.cpp, Ollama, vLLM, SGLang) need no key.

Suggested order: add `OPENAI_API_KEY` first, since the chart uses OpenAI as the reference row and the first-party
comparison (Gemini, Anthropic) is made against it. Then add providers one at a time and verify each (section 3).
`OPENROUTER_API_KEY` is worth early attention because a gateway key reaches many models.

| Tier | Provider id | Secret | Additional secrets | Note |
|---|---|---|---|---|
| reference | openai | `OPENAI_API_KEY` | — | reference row; also pulled in automatically by `all`, tiers and the first-party comparisons |
| p1 | cerebras | `CEREBRAS_API_KEY` | — |  |
| p1 | dashscope | `DASHSCOPE_API_KEY` | — |  |
| p1 | databricks | `DATABRICKS_API_KEY` | `DATABRICKS_HOST` | `DATABRICKS_HOST` is the workspace host name only |
| p1 | deepinfra | `DEEPINFRA_API_KEY` | — |  |
| p1 | deepseek | `DEEPSEEK_API_KEY` | — |  |
| p1 | fireworks | `FIREWORKS_API_KEY` | — |  |
| p1 | groq | `GROQ_API_KEY` | — |  |
| p1 | huggingface | `HUGGINGFACE_API_KEY` | — | router for several inference providers |
| p1 | mistral | `MISTRAL_API_KEY` | — |  |
| p1 | moonshot | `MOONSHOT_API_KEY` | — |  |
| p1 | nvidia | `NVIDIA_API_KEY` | — |  |
| p1 | perplexity | `PERPLEXITY_API_KEY` | — | OpenAI-compatible chat is the Orchestrator API (private preview, access needed) |
| p1 | together | `TOGETHER_API_KEY` | — |  |
| p1 | xai | `XAI_API_KEY` | — |  |
| p1 | zai | `ZAI_API_KEY` | — |  |
| p1 | openrouter | `OPENROUTER_API_KEY` | — | gateway: one key reaches many models |
| p2 | baseten | `BASETEN_API_KEY` | — |  |
| p2 | cloudflare | `CLOUDFLARE_API_KEY` | `CLOUDFLARE_ACCOUNT_ID` | `CLOUDFLARE_ACCOUNT_ID` is the account id; the key is an API token |
| p2 | cohere | `COHERE_API_KEY` | — |  |
| p2 | minimax | `MINIMAX_API_KEY` | — |  |
| p2 | nebius | `NEBIUS_API_KEY` | — |  |
| p2 | sambanova | `SAMBANOVA_API_KEY` | — |  |
| p2 | anthropic-openai | `ANTHROPIC_API_KEY` | — | first-party comparison |
| p2 | gemini-openai | `GEMINI_API_KEY` | — | first-party comparison |
| p3 | featherless | `FEATHERLESS_API_KEY` | — |  |
| p3 | hyperbolic | `HYPERBOLIC_API_KEY` | — |  |
| p3 | novita | `NOVITA_API_KEY` | — |  |
| p3 | parasail | `PARASAIL_API_KEY` | — |  |
| p3 | snowflake | `SNOWFLAKE_API_KEY` | `SNOWFLAKE_ACCOUNT` | `SNOWFLAKE_ACCOUNT` is the account identifier |
| p3 | cloudflare-gateway | `CLOUDFLARE_GATEWAY_API_KEY` | `CLOUDFLARE_ACCOUNT_ID` | same account id as the REST API base |
| p3 | vercel | `VERCEL_API_KEY` | — | gateway |

Not run in CI: TGI (maintenance mode, GPU-only start command) and Lambda (the Inference API appears retired).
Additional secrets are expanded into the base URL and are masked in logs.

## 3. Run a targeted probe to verify a key

Open **Actions → llm-probes → Run workflow** and fill in the inputs, or use the CLI.

Verify a new key with a single request first (`model-identity` sends one request of a few tokens):

```bash
gh workflow run llm-probes.yml --repo aklivity/zilla --ref <branch> \
  -f providers=groq -f probes=model-identity -f via=direct -f budget_usd=0.25
```

Then widen in steps:

| Goal | Inputs |
|---|---|
| Key works | `providers=<id> probes=model-identity via=direct` |
| One area | `providers=<id> probes=roles via=direct` (areas: output-cap, content-shape, images, roles, tools, tool-streaming, structured-output, streaming-usage, cached-tokens, reasoning, reasoning-replay, ranges, responses, errors, model-identity) |
| One probe | `probes=ranges.n-2` |
| Through Zilla too | `via=both zilla_ref=2.6.0-rc1` |
| All probes for one provider | `providers=<id> probes=all via=both` |
| Several providers | `providers=groq,deepseek,openrouter` |
| A whole tier | `providers=p1` (adds OpenAI as the reference row) |

Notes:

- `zilla_ref` is a published image tag, or a git ref built from source. No `develop` image is published, so `develop`
  builds Zilla in the workflow, which uses many Actions minutes. For routine runs pass a published tag such as `2.6.0-rc1`.
- `via=direct` skips Zilla entirely (no image pull or build).
- `budget_usd` is split equally between hosted providers; the runner stops probing a provider when its share is spent.
- Results: the run summary shows the chart; the `llm-probes-merged` artifact has `COMPATIBILITY.md` and `merged.json`;
  `results-<provider>` artifacts hold each provider's records, with credentials redacted.
- If **llm-probes** is not listed in the Actions tab for your branch, GitHub only lists `workflow_dispatch` workflows
  that exist on the default branch; the workflow then has to be merged first.
