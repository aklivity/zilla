# Providers for the `openai` dialect

Generated from `providers.json` by `python3 -m common.providers_md`; edit the JSON, not this file.
Docs were read on 2026-10-20.

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
| OpenAI | `OPENAI_API_KEY` | — |
| Cerebras Inference | `CEREBRAS_API_KEY` | — |
| Alibaba DashScope (Model Studio, Qwen) | `DASHSCOPE_API_KEY` | — |
| Databricks Foundation Model APIs | `DATABRICKS_API_KEY` | `DATABRICKS_HOST` |
| DeepInfra | `DEEPINFRA_API_KEY` | — |
| DeepSeek | `DEEPSEEK_API_KEY` | — |
| Fireworks AI | `FIREWORKS_API_KEY` | — |
| Groq | `GROQ_API_KEY` | — |
| Hugging Face Inference Providers | `HUGGINGFACE_API_KEY` | — |
| Mistral La Plateforme | `MISTRAL_API_KEY` | — |
| Moonshot AI (Kimi) | `MOONSHOT_API_KEY` | — |
| NVIDIA NIM (build.nvidia.com hosted) | `NVIDIA_API_KEY` | — |
| Perplexity (Orchestrator API) | `PERPLEXITY_API_KEY` | — |
| Together AI | `TOGETHER_API_KEY` | — |
| xAI Grok | `XAI_API_KEY` | — |
| Z.ai (Zhipu GLM) | `ZAI_API_KEY` | — |
| OpenRouter | `OPENROUTER_API_KEY` | — |
| llama.cpp server | none | — |
| Ollama | none | — |
| SGLang | none | — |
| vLLM | none | — |
| Baseten Model APIs | `BASETEN_API_KEY` | — |
| Cloudflare Workers AI | `CLOUDFLARE_API_KEY` | `CLOUDFLARE_ACCOUNT_ID` |
| Cohere (Compatibility API) | `COHERE_API_KEY` | — |
| MiniMax | `MINIMAX_API_KEY` | — |
| Nebius Token Factory (formerly AI Studio) | `NEBIUS_API_KEY` | — |
| SambaNova Cloud (SambaCloud) | `SAMBANOVA_API_KEY` | — |
| Hugging Face TGI | none | — |
| Anthropic OpenAI SDK compatibility | `ANTHROPIC_API_KEY` | — |
| Gemini OpenAI compatibility | `GEMINI_API_KEY` | — |
| Featherless AI | `FEATHERLESS_API_KEY` | — |
| Hyperbolic | `HYPERBOLIC_API_KEY` | — |
| Lambda Inference API | `LAMBDA_API_KEY` | — |
| Novita AI | `NOVITA_API_KEY` | — |
| Parasail | `PARASAIL_API_KEY` | — |
| Snowflake Cortex | `SNOWFLAKE_API_KEY` | `SNOWFLAKE_ACCOUNT` |
| Cloudflare AI Gateway | `CLOUDFLARE_GATEWAY_API_KEY` | `CLOUDFLARE_ACCOUNT_ID` |
| Vercel AI Gateway | `VERCEL_API_KEY` | — |

Local hosts need no secret. Additional secrets are expanded into the base URL. Optional repository settings are not used:
models come from `providers.json`.

## Reference · hosted

### OpenAI (`openai`)

- Tier / hosting: Reference / hosted
- Secret: `OPENAI_API_KEY`
- Base URL: `https://api.openai.com/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <API key or workload-identity access token>. Optional OpenAI-Organization and OpenAI-Project headers (API overview page).
- Models: chat `gpt-5-nano`, reasoning `gpt-6-luna` (official-page) — gpt-5-nano ($0.05 in / $0.40 out per 1M) is the cheapest text model with a model page; it is a reasoning-token model (page: 'Reasoning token support'), Chat Completions and Responses supported. gpt-4.1-nano ($0.10/$0.40) is the cheapest non-reasoning option. gpt-6-luna ($0.10/$0.50, 'most efficient model for focused, high-volume tasks', 1.05M context) is the current-generation cost tier: reasoning.effort supports none, low, medium (default), high, xhigh, max; Chat Completions function calling only with reasoning_effort=none. gpt-6.1-sol and gpt-6-astra are the higher tiers on the featured list. All ids confirmed on developers.openai.com/api/docs/models and the per-model .md pages 2026-10-20. Earlier reference-doc facts (round 1, chat.md / responses create.md): max_completion_tokens bounds visible + reasoning tokens; max_tokens deprecated and not compatible with o-series; developer role messages replace system with o1 and newer; reasoning_effort values none, minimal, low, medium, high, xhigh, max ('Not all reasoning models support every value'); stream_options.include_usage adds a final usage chunk with empty choices, other chunks carry usage null; usage.prompt_tokens_details.cached_tokens and usage.completion_tokens_details.reasoning_tokens documented; Responses store defaults true; temperature 0..2; stop up to 4 sequences; seed best-effort; logprobs/top_logprobs 0..20.
- Price (chat model, USD per million tokens): input 0.05, output 0.4 (official-page (developers.openai.com/api/docs/models/gpt-5-nano.md). gpt-6-luna: 0.10 / 0.50 (models/gpt-6-luna.md))
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (not checked)
- Verification: **verified**
- Documented deviations:
  - gpt-6-luna model page: Chat Completions supports function calling only with reasoning_effort set to none; use the Responses API for built-in tools and function calling — probes: `tools.tool-required`, `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
- Docs read:
  - https://developers.openai.com/api/reference/overview.md — fetched, 2026-10-20
  - https://developers.openai.com/api/reference/resources/chat.md — fetched, 2026-10-20
  - https://developers.openai.com/api/reference/resources/responses/methods/create.md — fetched, 2026-10-20
  - https://developers.openai.com/api/reference/chat-completions/overview.md — fetched, 2026-10-20
  - https://developers.openai.com/api/docs/models.md — fetched, 2026-10-20
  - https://developers.openai.com/api/docs/models/gpt-6-luna.md — fetched, 2026-10-20
  - https://developers.openai.com/api/docs/models/gpt-5-nano.md — fetched, 2026-10-20
  - https://developers.openai.com/api/docs/models/gpt-4.1-nano.md — fetched, 2026-10-20
  - https://developers.openai.com/api/docs/guides/rate-limits.md — fetched, 2026-10-20
  - https://developers.openai.com/api/docs/guides/error-codes.md — fetched, 2026-10-20
- Notes: Reference. Base URL now confirmed on the API overview page (curl https://api.openai.com/v1/models example). Rate-limit headers documented on the rate-limits and overview pages: x-ratelimit-limit/remaining/reset-requests and -tokens (plus project-token variants), Retry-After on temporary 429/503; also openai-organization, openai-processing-ms, openai-version, x-request-id. Error codes page documents error type/code pairs (e.g. rate_limit_error, service_unavailable_error). Chat Completions create page 404s at .../methods/create.md; parameters live in resources/chat.md. Chat Completions overview says new projects should try Responses. Round 1 wrongly said gpt-5-nano/gpt-4.1-nano are not listed; they are on the models catalog today.

## Priority 1 · hosted

### Cerebras Inference (`cerebras`)

- Tier / hosting: Priority 1 / hosted
- Secret: `CEREBRAS_API_KEY`
- Base URL: `https://api.cerebras.ai/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <CEREBRAS_API_KEY> (securityScheme BearerAuth in the API reference).
- Models: chat `gpt-oss-120b`, reasoning `gpt-oss-120b` (official-page) — Model Catalog lists exactly two Shared Inference models: gpt-oss-120b and qwen-3.8-27b (llama3.1-8b is no longer listed). gpt-oss-120b is the cheapest ($0.35/$0.75 vs qwen-3.8-27b $0.99/$1.49) and is a reasoning model (reasoning_effort low/medium/high, default medium, reasoning cannot be disabled). qwen-3.8-27b is the alternative reasoning model (supports reasoning_effort none) and the only vision model. Free tier for gpt-oss-120b: 30 req/min, 60k input tokens/min, 1M tokens/day; context 65k free / 131k paid; max output 32k free / 40k paid.
- Price (chat model, USD per million tokens): input 0.35, output 0.75 (official-page)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered False (official-page)
- Verification: **verified**
- Documented deviations:
  - OpenAI compatibility page: only n: 1 is supported (API reference schema enum [1]). — probes: `ranges.n-2`; expect: unsupported (official-page)
  - Tool streaming: use standard streaming with stream: true; do not send tool_stream. — probes: `tool-streaming.tool-stream`; expect: unsupported (official-page)
  - Images: standard image_url content parts, but the url must be a base64 PNG or JPEG data URI; external HTTPS image URLs and image_url.detail are not supported (400 'HTTPS image URLs are not supported'); images only on user messages and only on vision-capable models (qwen-3.8-27b); gpt-oss-120b has no image input. — probes: `images.image-part`; expect: adjustment-needed (official-page)
  - service_tier is not available with Shared Inference (private preview, dedicated endpoints only); prediction is limited by endpoint and model availability and is not available for qwen-3.8-27b on shared endpoints. — probes: no probe; expect: unsupported (official-page)
  - Combining tools with response_format is model-dependent; gpt-oss-120b rejects requests containing both fields. — probes: no probe; expect: adjustment-needed (official-page)
  - developer role is supported only by gpt-oss-120b; system and developer both map to a developer-level instruction layer, so the same prompt may behave differently than on OpenAI. — probes: `roles.developer-role`; expect: compliant (official-page)
  - max_tokens is an alias of max_completion_tokens (do not send both); reasoning tokens count toward max_completion_tokens and completion usage. — probes: `output-cap.max-completion-tokens`, `output-cap.max-tokens`; expect: compliant (official-page)
  - stop accepts up to four sequences. — probes: `ranges.stop-5`; expect: unsupported (official-page)
  - temperature range 0-2; logprobs (boolean), top_logprobs 0-20, seed (best effort, determinism not guaranteed), frequency_penalty/presence_penalty -2..2 and logit_bias are documented request parameters. — probes: `ranges.temperature-2`, `ranges.logprobs`, `ranges.seed`; expect: compliant (official-page)
  - response_format json_schema with strict:true uses constrained decoding but requires additionalProperties:false on every object and rejects recursive schemas, oneOf/allOf/not, nullable:true, if/then/else, patternProperties, root unions; json_object is not compatible with streaming (stream must be false). — probes: `structured-output.json-schema-strict`; expect: adjustment-needed (official-page)
  - gpt-oss-120b reasoning_effort accepts low, medium, high only (default medium; none not supported); Cerebras-specific reasoning_format (parsed/raw/hidden/none) and clear_thinking must be passed via extra_body in OpenAI clients; with parsed format reasoning is returned in message.reasoning / delta.reasoning. — probes: `reasoning.reasoning-effort`, `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - Assistant messages may carry a 'reasoning' field supplied back in history; supported behavior varies by model. — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Usage reports prompt_tokens_details.cached_tokens (prompt caching is automatic). — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Error codes listed: 400, 401, 402, 403, 404, 413, 422 (UnprocessableEntityError), 429, 500, 503. Round-1 claim that validation errors return 400 instead of 422 is not supported by the docs. Error body shape and rate-limit headers are not documented in the fetched pages. — probes: `errors.error-shape`; expect: adjustment-needed (official-page)
  - The Chat Completions request schema does not list stream_options (include_usage), so streaming usage behavior is undocumented. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
- Docs read:
  - https://inference-docs.cerebras.ai/resources/openai.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/api-reference/chat-completions.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/models/overview.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/models/openai-oss.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/models/qwen-3.8-27b.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/capabilities/reasoning.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/capabilities/structured-outputs.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/capabilities/streaming.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/capabilities/prompt-caching.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/support/error.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/support/rate-limits.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/api-reference/models/public-models.md — fetched, 2026-10-20
  - https://inference-docs.cerebras.ai/support/pricing.md — not-reached, 2026-10-20
- Notes: Compatibility table, request-parameter schema and prices read from the official pages (pricing page itself returned 403 through the proxy; prices taken from the model pages $0.35/$0.75 gpt-oss-120b, $0.99/$1.49 qwen-3.8-27b). Round 1's qwen-3.8-27b reference and gpt-oss-120b pricing were right; its 'only gpt-oss-120b in catalog' and '400 instead of 422' claims were wrong. No Responses or Anthropic Messages endpoint appears in the docs index. Free tier exists (rate limits per model page); CI-friendly with a free key. inference-docs.cerebras.ai fails WebFetch DNS but works via curl.

### Alibaba DashScope (Model Studio, Qwen) (`dashscope`)

- Tier / hosting: Priority 1 / hosted
- Secret: `DASHSCOPE_API_KEY`
- Base URL: `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` (official-page)
- Auth: bearer via `Authorization` — Model Studio API key as Bearer token. Keys are bound to the region they were created in; a key from another region returns HTTP 401 'Incorrect API key provided' (code invalid_api_key). Docs now recommend workspace-specific domains (Singapore: https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1; Virginia: {WorkspaceId}.us-east-1; Beijing: {WorkspaceId}.cn-beijing; Hong Kong: {WorkspaceId}.cn-hongkong; Tokyo: {WorkspaceId}.ap-northeast-1; Frankfurt: {WorkspaceId}.eu-central-1 on the Responses page). base_url above is the legacy Singapore host the docs say to migrate from; the Responses and Anthropic pages state 'The existing domain remains fully functional'. Legacy Beijing host: https://dashscope.aliyuncs.com/compatible-mode/v1. The chat page itself only lists the workspace domains, so prefer the workspace URL if a WorkspaceId is available.
- Models: chat `qwen3.7-flash`, reasoning `qwen3.8-flash` (official-page) — qwen3.7-flash is the cheapest current model on the Singapore pricing page (Qwen-Flash table, tiered: <=32K input tokens $0.03 in / $0.13 out). qwen3.8-flash ($0.15 / $0.47, <=1M tokens) is listed on the Recommended-models page and takes reasoning_effort low/medium/xhigh (default xhigh) or enable_thinking. qwen-flash ($0.05/$0.40 <=256K) and qwen3.5-flash ($0.1/$0.4) also exist. Both chosen ids appear in the Responses-API and Anthropic-endpoint supported-model lists. Whether qwen3.7-flash thinks by default was not determined (default table is behind a 'Supported models' link not read). Rate limits Singapore: qwen3.7-flash 15,000 RPM / 5,000,000 TPM; qwen3.8-flash 'Dynamic Rate Limiting'.
- Price (chat model, USD per million tokens): input 0.03, output 0.13 (official-page: Singapore (International deployment) qwen3.7-flash, input tokens per request 0<Token<=32K; higher tiers 32K-256K $0.10/$0.40, 256K-1M $0.20/$0.80. Free quota in Singapore only (1M tokens, valid 90 days from activation).)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/apps/anthropic (SDK base_url; POST .../apps/anthropic/v1/messages). Auth via x-api-key or Authorization: Bearer. Supported list includes qwen3.7-flash, qwen3.8-flash, qwen-flash.` (official-page)
- Verification: **verified**
- Documented deviations:
  - temperature valid range is [0, 2) (2 itself excluded); a value of 0 is not recommended; do not set both temperature and top_p — probes: `ranges.temperature-2`; expect: adjustment-needed (official-page)
  - top_p valid range is (0, 1.0]; top_k, repetition_penalty are non-standard and go in extra_body (Python SDK) — probes: no probe; expect: adjustment-needed (official-page)
  - n valid values 1-4; chat API reference says only supported by Qwen3 (non-thinking mode) models, compat page says only qwen-plus; n is fixed at 1 when tools is set; n=0 equals n=1 — probes: `ranges.n-2`; expect: adjustment-needed (official-page)
  - seed supported (range [0, 2^31-1] on the chat reference; compat page says unsigned 64-bit); reproducibility is best-effort — probes: `ranges.seed`; expect: compliant (official-page)
  - stop accepts a string or an array; array elements cannot mix token_ids and strings; no maximum count documented — probes: `ranges.stop-5`; expect: compliant (official-page)
  - logprobs/top_logprobs supported only for qwen-plus snapshot models (not stable alias), qwen3-vl-plus series and Qwen3 open-source models; top_logprobs range [0, 5]; reasoning_content gets no logprobs — probes: `ranges.logprobs`; expect: adjustment-needed (official-page)
  - Only messages[0] may have the system role; valid roles listed are system, user, assistant (and tool for results); no developer role — probes: `roles.two-system`, `roles.developer-role`; expect: unsupported (official-page)
  - Compat page: 'In general, user and assistant roles alternate, and the last element must have the user role' — probes: `roles.consecutive-user`; expect: adjustment-needed (official-page)
  - Message name field is not listed in the message schemas (system/user/assistant/tool objects have content and role only; tool has tool_call_id) — probes: `roles.name-field`; expect: adjustment-needed (official-page)
  - User content may be a string or an array of parts (text, image_url, input_audio, video, video_url); system and assistant content are documented as strings — probes: `content-shape.content-parts`; expect: compliant (official-page)
  - image_url parts take a URL or Base64 data URL, but only multimodal models (Qwen-VL/Omni, newer Qwen3.x multimodal) accept them; min_pixels/max_pixels/vl_high_resolution_images are non-standard — probes: `images.image-part`; expect: adjustment-needed (official-page)
  - max_tokens is 'to be deprecated'; meaning varies by model (glm-5.3 and deepseek-v4 count answer plus chain-of-thought, 'other models' count the answer only) — probes: `output-cap.max-tokens`; expect: adjustment-needed (official-page)
  - max_completion_tokens supported for Qwen Flash 3.5 and later (and Max 3.7+, Plus 3.5+, Kimi k2.5+, GLM 5+, MiniMax M2.5+, DeepSeek v4-pro+); limits chain-of-thought plus answer; may overshoot by up to 10 tokens — probes: `output-cap.max-completion-tokens`; expect: compliant (official-page)
  - tool_choice: Qwen series do not support 'required'; thinking-mode models do not support forcing a specific function (named tool_choice); default auto; parallel_tool_calls defaults to false — probes: `tools.tool-required`; expect: unsupported (official-page)
  - Compat page: the tools parameter cannot be used with stream=True at the same time (while the chat reference documents a non-standard tool_stream flag for Qwen3.5+/GLM series; docs conflict) — probes: `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
  - response_format supports text, json_object, json_schema (with strict); json_object and json_schema are supported by different models; json_object requires the word JSON in the prompt or the call errors — probes: `structured-output.json-object`, `structured-output.json-schema-strict`; expect: adjustment-needed (official-page)
  - stream_options.include_usage defaults to false and only takes effect when stream is true; usage arrives in the final chunk (choices empty) — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - usage.prompt_tokens_details.cached_tokens is returned (implicit and explicit caching; explicit caching uses cache_control: {type: ephemeral} on user content parts) — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Thinking toggled by non-standard enable_thinking (extra_body in Python SDK, top-level over raw HTTP) plus thinking_budget; thinking text returned in reasoning_content — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort is standard but valid values/defaults vary per model: qwen3.8 family xhigh (default)/medium/low with max,high->xhigh, minimal->low, none->enable_thinking=false; glm-5.3 and kimi-k3 low/high/max; DeepSeek-V4 high/max; other values error; cannot be combined with thinking_budget on qwen3.8 — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - preserve_thinking (default true on qwen3.8-max/flash) requires historical reasoning_content to be sent back in assistant messages, not concatenated into content; billed as input — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - presence_penalty supported only on Qwen commercial models and open-source qwen1.5 and later; system_fingerprint is returned as an empty string — probes: no probe; expect: adjustment-needed (official-page)
  - Responses API (/compatible-mode/v1/responses) is supported for listed models incl. qwen3.7-flash and qwen3.8-flash; previous_response_id keeps context, response ids valid 7 days; models outside the list get only basic compatibility; a legacy path /api/v2/apps/protocols/compatible-mode/v1/responses is unmaintained — probes: `responses.responses-basic`, `responses.responses-store`; expect: compliant (official-page)
  - Errors: wrong-region key gives HTTP 401 with code invalid_api_key and message 'Incorrect API key provided'; RPM/TPM breaches return 429; exhausted free quota returns 403; rate-limit response headers are not documented — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
- Docs read:
  - https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope — fetched, 2026-10-20
  - https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-chat-completions — fetched, 2026-10-20
  - https://www.alibabacloud.com/help/en/model-studio/getting-started/models — fetched, 2026-10-20
  - https://www.alibabacloud.com/help/en/model-studio/model-pricing — fetched, 2026-10-20
  - https://www.alibabacloud.com/help/en/model-studio/compatibility-with-openai-responses-api — fetched, 2026-10-20
  - https://www.alibabacloud.com/help/en/model-studio/anthropic-api-messages — fetched, 2026-10-20
  - https://www.alibabacloud.com/help/en/model-studio/rate-limit — fetched, 2026-10-20
- Notes: Read via curl through the sandbox proxy (WebFetch itself fails DNS for every host; curl reaches www.alibabacloud.com, not help.aliyun.com/dashscope.aliyuncs.com APIs). Pages show 'Last Updated' Oct 11-19, 2026. Intl and China regions are separate accounts/keys. Free quota is Singapore only (90 days). Not read: deep-thinking page, error-code page, GET /models. The compat page and the chat API reference disagree on n (qwen-plus vs Qwen3 non-thinking) and seed range; the chat API reference is the newer-looking page.

### Databricks Foundation Model APIs (`databricks`)

- Tier / hosting: Priority 1 / hosted
- Secret: `DATABRICKS_API_KEY`
- Base URL: `https://${DATABRICKS_HOST}/ai-gateway/mlflow/v1` (official-page)
- Auth: bearer via `Authorization` — Databricks personal access token (dapi...) or OAuth token as Bearer; docs examples use curl -u token:$DATABRICKS_TOKEN and api_key=<token> in the OpenAI client. Base URL is workspace-specific. Docs show two chat surfaces: Unity Gateway model services at <workspace>/ai-gateway/mlflow/v1 (model = system.ai.<name>, e.g. system.ai.meta-llama-3-1-8b-instruct) and legacy serving endpoints at <workspace>/serving-endpoints (model = endpoint name, e.g. databricks-meta-llama-3-1-8b-instruct).
- Models: chat `system.ai.meta-llama-3-1-8b-instruct`, reasoning `system.ai.gpt-oss-20b` (official-page) — Supported Models page lists 'Model service name: system.ai.meta-llama-3-1-8b-instruct / Endpoint name: databricks-meta-llama-3-1-8b-instruct' and 'system.ai.gpt-oss-20b / databricks-gpt-oss-20b' (GPT OSS 20B is a lightweight reasoning-only model, reasoning_effort low/medium/high, default medium, 'none' treated as unset). Both are also in the Query-foundation-models list. Pricing is DBU-based and not published on these pages, so 'cheapest' is unverified; Llama 3.1 8B is the smallest chat model listed (max output 8,192 tokens). Use endpoint names (databricks-*) when calling the serving-endpoints base URL.
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://<workspace-host>/ai-gateway/anthropic/v1/messages` (official-page)
- Verification: **verified**
- Documented deviations:
  - Two chat base URL patterns are documented: <workspace>/ai-gateway/mlflow/v1 (Unity Gateway model services, system.ai.* names) and <workspace>/serving-endpoints (endpoint names databricks-*); the Responses path is /ai-gateway/openai/v1/responses (also /serving-endpoints/responses). Foundation model requests are routed through Unity Gateway. — probes: `model-identity.model-identity`; expect: adjustment-needed (official-page)
  - Chat request fields documented: messages, max_tokens, stream, temperature, top_p, top_k, stop, n, tool_choice, tools, response_format, logprobs, top_logprobs, reasoning_effort, service_tier; max_completion_tokens is not listed. — probes: `output-cap.max-completion-tokens`, `output-cap.max-tokens`; expect: adjustment-needed (official-page)
  - stream defaults to true in the Chat request table. — probes: no probe; expect: adjustment-needed (official-page)
  - Message roles are system, user, assistant, tool (no developer role); list-of-parts content is documented for external Claude models only, use string content for other providers/open models; image content must be base64 data (URLs not supported). — probes: `roles.developer-role`, `content-shape.content-parts`, `images.image-part`; expect: adjustment-needed (official-page)
  - The n parameter is only available for provisioned throughput endpoints. — probes: `ranges.n-2`; expect: unsupported (official-page)
  - Function is the only supported tool type, max 32 functions; parallel function calling is not supported (public preview limitation); tool JSON schema subset: no pattern, anyOf/oneOf/allOf/prefixItems/$ref, type lists except [type,'null'], max 16 keys; function description is required. — probes: `tools.tool-required`; expect: adjustment-needed (official-page)
  - Structured outputs are OpenAI-compatible for Foundation Model APIs (json_schema with strict; schema subset, max 64 keys). For Claude models only json_schema is supported (json_object not supported), structured outputs are not supported with streaming, and response_format cannot be combined with tools/tool_choice. — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: adjustment-needed (official-page)
  - reasoning_effort is only accepted by a limited set of models; accepted values (none/minimal/low/medium/high/xhigh/max) and defaults vary by model; GPT OSS accepts low/medium/high ('minimal'->low, 'xhigh'/'max'->high, 'none' treated as unset); GLM 5.3 Flash and GLM 5.3 reject 'none'. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - Reasoning output shape varies by model family (Claude via Anthropic Messages thinking/output_config.effort; reasoning content returned as content items with summary) and differs from OpenAI fields. — probes: `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Pay-per-token Responses API: background, store and previous_response_id are not supported and return a 400 error; 'conversation' is also unsupported on the Open Responses surface; the native OpenAI Responses passthrough is for OpenAI models and external models support all parameters; code interpreter and web search tools are not supported. — probes: `responses.responses-basic`, `responses.responses-store`; expect: unsupported (official-page)
  - Prompt caching cache_control is accepted only by Databricks-hosted Claude models. — probes: `cached-tokens.cached-tokens`; expect: adjustment-needed (official-page)
  - Rate limits for pay-per-token endpoints are ITPM, OTPM and queries-per-hour per model; max_tokens is used to reserve output-token capacity and a request that would exceed OTPM is rejected immediately with 429; workspace QPS 200; request payload 4 MB; model execution limit 597 seconds per request (30 minutes for streaming to open models, not the legacy Llama 3.1 models). — probes: `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - Max output tokens per response: Llama 3.1 8B / 3.3 70B / Gemma 3 12B / Llama 4 Maverick 8,192; GPT OSS 20B/120B 25,000; DeepSeek V4 384,000. — probes: `output-cap.max-tokens`; expect: adjustment-needed (official-page)
  - Response/stream note: output returned in choices; usage returned in every chunk when streaming (Anthropic-format differences documented for Claude); finish_reason maps end_turn/stop_sequence/max_tokens/tool_use to stop/stop/length/tool_calls. — probes: `streaming-usage.usage-included`; expect: adjustment-needed (official-page)
- Docs read:
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/score-foundation-models — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/query-chat-models — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/foundation-model-apis/api-reference — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/foundation-model-apis/supported-models — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/query-reason-models — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/function-calling — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/structured-outputs — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/query-openai-responses — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/foundation-model-apis/limits — fetched, 2026-10-20
  - https://docs.databricks.com/aws/en/machine-learning/model-serving/model-serving-limits — fetched, 2026-10-20
  - https://learn.microsoft.com/en-us/azure/databricks/machine-learning/model-serving/score-foundation-models — fetched, 2026-10-20
- Notes: Read from docs.databricks.com (AWS) via curl and the Azure Databricks mirror on learn.microsoft.com via WebFetch (docs.databricks.com fails WebFetch DNS). Requires a Databricks workspace; the base URL is a pattern, so record the host in a DATABRICKS_BASE_URL-style variable (secret name follows the brief; docs use DATABRICKS_TOKEN). Pay-per-token pricing is DBU-based and was not found, so price is null. Not CI-friendly without a paid workspace. Model catalog churns quickly (docs list GPT-6, Claude Fable/Opus 5.x, Gemini 4 etc.); confirm availability in the target workspace and region.

### DeepInfra (`deepinfra`)

- Tier / hosting: Priority 1 / hosted
- Secret: `DEEPINFRA_API_KEY`
- Base URL: `https://api.deepinfra.com/v1/openai` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <DEEPINFRA_API_KEY>; the API reference also lists x-api-key / xi-api-key header variants.
- Models: chat `deepseek-ai/DeepSeek-V4-Flash-0731`, reasoning `deepseek-ai/DeepSeek-V4-Flash-0731` (official-page) — This id is used throughout the official chat docs and is listed on the Reasoning Models page (also DeepSeek-V4-Pro-0813, zai-org/GLM-5.2, moonshotai/Kimi-K3, inclusionAI/Ling-3.0-flash). It is a hybrid reasoning model: reasoning_effort 'none' (or reasoning.enabled=false) turns reasoning off, so the same id serves as chat and reasoning model. Whether it is the cheapest chat model could not be verified: the model catalog/pricing pages (deepinfra.com) are unreachable; round 1's meta-llama/Meta-Llama-3.1-8B-Instruct is not mentioned in the current docs.
- Price (chat model, USD per million tokens): input 0.09, output 0.18 (search-summary)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True, `https://api.deepinfra.com/anthropic/v1/messages` (official-page)
- Verification: **verified**
- Documented deviations:
  - Docs state the API may not be 100% compatible with all OpenAI parameters. — probes: no probe; expect: adjustment-needed (official-page)
  - Supported parameters table: model, messages (roles system, user, assistant), max_tokens, stream, temperature (0-2), top_p, stop, n, presence_penalty, frequency_penalty, response_format, tools/tool_choice, service_tier (priority/flex), fail_fast, reasoning_effort. The API reference additionally lists seed, logprobs, stream_options, top_k, min_p, repetition_penalty, reasoning, prompt_cache_key, chat_template_kwargs and others; max_completion_tokens is not listed. — probes: `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - max_tokens: hard cap 16384 tokens for most models; unset max_tokens defaults to model context minus input or 65536, whichever is smaller; response continuation by trailing assistant message. — probes: `output-cap.max-tokens`; expect: compliant (official-page)
  - Messages schema: (user, assistant, tool)* plus one system message anywhere; no developer role in the schema; optional name on user/assistant/system messages. — probes: `roles.developer-role`, `roles.two-system`, `roles.name-field`; expect: adjustment-needed (official-page)
  - n: 1-4 (default 1). — probes: `ranges.n-2`; expect: compliant (official-page)
  - stop: overview table says up to 4 sequences, API reference schema says up to 16 sequences (docs disagree). — probes: `ranges.stop-5`; expect: compliant (official-page)
  - temperature 0-2; seed accepted (determinism not guaranteed); logprobs/top_logprobs (1-20) supported across all request modes. — probes: `ranges.temperature-2`, `ranges.seed`, `ranges.logprobs`; expect: compliant (official-page)
  - Streaming: usage arrives in the final chunk together with finish_reason (choices not empty); stream_options.include_usage defaults to true (OpenAI default is off); continuous_usage_stats optional. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
  - response_format: text, json_object, json_schema (strict:true recommended) and a non-standard regex type; JSON mode can increase hallucination. — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (official-page)
  - Tool calling: single and parallel calls (quality may vary), tool_choice auto/none (reference also lists required and a named function), streaming supported, nested calls not supported; docs advise avoiding system messages when using tool calling. — probes: `tools.tool-required`, `tool-streaming.tool-stream`; expect: compliant (official-page)
  - Images: image_url accepts a public URL or a base64 data URI on vision models. — probes: `images.image-part`; expect: compliant (official-page)
  - reasoning_effort (none, minimal, low, medium, high, xhigh, max per reference; overview/reasoning page list none/low/medium/high) is a top-level parameter (docs show extra_body in the Python SDK); a `reasoning` object {effort, enabled} is also supported; non-reasoning models silently ignore both; reasoning tokens bill as output. — probes: `reasoning.reasoning-effort`; expect: compliant; silent (official-page)
  - Assistant messages accept reasoning_content (string) for replay. — probes: `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Prompt caching is automatic; usage.prompt_tokens_details.cached_tokens reports cached tokens; prompt_cache_key and prompt_cache_options (ttl 5m/1h) optional. — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Rate limit: 200 concurrent requests per model (not per-minute); 429 'Rate limited'; fail_fast returns 429 with OpenAI-style error {message,type:invalid_request_error,param,code:engine_overloaded}; no rate-limit headers documented. — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - service_tier priority (+50%) / flex (-20%) on tagged models; an unsupported tier is served at standard tier with no error. — probes: no probe; expect: adjustment-needed; silent (official-page)
- Docs read:
  - https://docs.deepinfra.com/chat/overview.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/api-reference/chat-completions/openai-chat-completions.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/reasoning.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/streaming.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/tool-calling.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/structured-outputs.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/prompt-caching.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/log-probs.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/chat/vision.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/account/rate-limits.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/integrations/anthropic.md — fetched, 2026-10-20
  - https://docs.deepinfra.com/models.md — fetched, 2026-10-20
  - https://deepinfra.com/docs/openai_api — not-reached, 2026-10-20
  - https://deepinfra.com/pricing — not-reached, 2026-10-20
- Notes: docs.deepinfra.com .md pages and the OpenAPI chat-completions reference were read via curl (WebFetch DNS fails). deepinfra.com and api.deepinfra.com are blocked by the proxy, so price ($0.09 in / $0.18 out for DeepSeek-V4-Flash-0731) is search-summary from DeepInfra's model page as quoted by a web search; Responses endpoint and GET /models are not documented in the fetched pages (round 1's models_list=true removed). Anthropic Messages endpoint documented at https://api.deepinfra.com/anthropic (POST /anthropic/v1/messages, also count_tokens). Also an HF router provider.

### DeepSeek (`deepseek`)

- Tier / hosting: Priority 1 / hosted
- Secret: `DEEPSEEK_API_KEY`
- Base URL: `https://api.deepseek.com` (official-page)
- Auth: bearer via `Authorization` — Examples send 'Authorization: Bearer ${DEEPSEEK_API_KEY}'; chat endpoint is {base_url}/chat/completions (no /v1 needed).
- Models: chat `deepseek-flash`, reasoning `deepseek-flash` (official-page) — Models & Pricing lists only deepseek-flash (DeepSeek-V4.1-Flash-1017) and deepseek-v4-pro (DeepSeek-V4-Pro-0813). Thinking mode is ON by default for both: send thinking:{type:'disabled'} (or reasoning_effort none) for plain chat and thinking enabled with reasoning_effort low/high/max for reasoning. Legacy names deepseek-v4-flash and deepseek-v4-flash-vision-exp are accepted but retired (served by flash). deepseek-chat / deepseek-reasoner are not mentioned on the pages read.
- Price (chat model, USD per million tokens): input 0.3, output 1.2 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://api.deepseek.com/anthropic` (official-page)
- Verification: **verified**
- Documented deviations:
  - The chat request schema lists model enum [deepseek-flash, deepseek-v4-pro]; legacy deepseek-v4-flash names are still accepted but served by DeepSeek-V4.1-Flash-1017 and billed at Flash price. — probes: `model-identity.model-identity`; expect: adjustment-needed (official-page)
  - stop accepts up to 16 sequences. — probes: `ranges.stop-5`; expect: compliant (official-page)
  - temperature (<=2) has no effect in thinking mode, which is the default; presence_penalty and frequency_penalty are deprecated and have no effect without raising an error. — probes: `ranges.temperature-2`; expect: adjustment-needed; silent (official-page)
  - top_p only takes effect in thinking mode on deepseek-flash (effective range 0.95-1.0, lower values clamped to 0.95); on deepseek-v4-pro it is ignored in thinking mode (fixed 0.95 with tools, otherwise 1.0). — probes: no probe; expect: adjustment-needed; silent (official-page)
  - tool_choice required and named-function choices are not supported in thinking mode and return a 400; disable thinking first. — probes: `tools.tool-required`; expect: adjustment-needed (official-page)
  - response_format accepts only text or json_object (no json_schema); JSON mode requires the prompt itself to ask for JSON or the model may emit unending whitespace. Tool-call strict mode is a Beta feature. — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: unsupported (official-page)
  - max_tokens range is 1 to 384K; default 8K in non-thinking mode, 64K in thinking mode (128K with reasoning_effort max). max_completion_tokens is not in the documented request schema. — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - n and seed are not listed in the documented chat completion request schema. — probes: `ranges.n-2`, `ranges.seed`; expect: unsupported (official-page)
  - logprobs (boolean) and top_logprobs (<=20) are listed in the request schema. — probes: `ranges.logprobs`; expect: compliant (official-page)
  - Thinking is toggled with a non-standard thinking:{type: enabled|disabled} body field (default enabled; pass via extra_body in the OpenAI SDK) and reasoning_effort accepts none, low, high, max. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - reasoning_effort mapping: minimal->low, medium->high, xhigh->high, ultra->max are accepted for compatibility and silently remapped; default effort is high. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed; silent (official-page)
  - Chain of thought is returned in message.reasoning_content at the same level as content (thinking mode only). — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - If the request carries tools, reasoning_content of all previous turns must be passed back; without tools it is ignored and not added to the context. — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - stream_options requires stream:true (else 400). The final content chunk always carries usage even when include_usage is omitted; no separate usage-only chunk is emitted and that last chunk has exactly one choice with no new content. — probes: `streaming-usage.usage-default`, `streaming-usage.usage-included`; expect: adjustment-needed (official-page)
  - usage includes prompt_tokens_details.cached_tokens plus DeepSeek's prompt_cache_hit_tokens and prompt_cache_miss_tokens. — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - User messages accept content parts text, image_url (jpeg/png/gif/webp, http(s) or data URL, detail low/high/original/auto) and file; images are supported on deepseek-flash only (deepseek-v4-pro: vision not supported). — probes: `images.image-part`, `content-shape.content-parts`; expect: compliant (official-page)
  - message name is documented as an optional participant name on system, user, assistant messages. — probes: `roles.name-field`; expect: compliant (official-page)
  - Chat message roles in the schema are system, user, assistant, tool; developer is not listed. — probes: `roles.developer-role`; expect: adjustment-needed (official-page)
  - Error codes: 400 invalid format, 401, 402 insufficient balance, 422 invalid parameters, 429 rate limit/concurrency, 500, 503; concurrency limit per account is 2500 (flash) / 500 (v4-pro) and exceeding it returns 429; no rate-limit headers are documented. — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - Responses API (base_url https://api.deepseek.com) is supported for deepseek-flash and deepseek-v4-pro with function tools and streaming semantic events (no [DONE]). — probes: `responses.responses-basic`; expect: compliant (official-page)
  - Responses API: previous_response_id is not supported (stateless API). — probes: `responses.responses-store`; expect: unsupported; silent (official-page)
  - Responses API: conversation is not supported (stateless API). — probes: `responses.responses-store`; expect: unsupported; silent (official-page)
  - Responses API: store is not supported; the response always carries store: false. — probes: `responses.responses-store`; expect: unsupported; silent (official-page)
  - Responses API: background is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: metadata is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: include is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: prompt (reusable prompts) is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: truncation is not supported; requests exceeding the context window return a 400. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: service_tier is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: safety_identifier is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: prompt_cache_key / prompt_cache_retention are not supported (context caching is automatic). — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: context_management is not supported. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: stream_options is not supported. — probes: `streaming-usage.usage-included`; expect: unsupported; silent (official-page)
  - Responses API: parallel_tool_calls is ignored (parallel tool calling is always enabled). — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: max_tool_calls is ignored. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: reasoning.summary is accepted but no summary is generated. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: text.verbosity is accepted but has no effect. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: tools other than function (and web_search on deepseek-v4-pro, and the apply_patch custom tool) are ignored: file_search, code_interpreter, computer_use, mcp; web_search is ignored with deepseek-flash. — probes: no probe; expect: unsupported; silent (official-page)
  - Responses API: developer role messages are treated as user; File inputs are not supported; reasoning input items drop summary and encrypted_content. — probes: `roles.developer-role`; expect: unsupported; silent (official-page)
  - Anthropic endpoint: anthropic-version header is ignored; anthropic-beta is ignored for /messages (except files-api-2025-04-14 for file image sources). — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Anthropic endpoint: container, mcp_servers and service_tier are ignored; metadata supports only user_id. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Anthropic endpoint: thinking is supported but budget_tokens is ignored; output_config supports only effort; top_k is ignored. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Anthropic endpoint: cache_control (tools, text, tool_use, tool_result), citations and tool_result is_error are ignored; disable_parallel_tool_use is ignored for tool_choice auto/any/tool. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Anthropic endpoint: document, search_result, redacted_thinking, code_execution_tool_result, mcp_tool_use, mcp_tool_result and container_upload content blocks are not supported. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic endpoint: Model names starting with claude are accepted: claude-opus* maps to deepseek-v4-pro and other claude* to deepseek-flash; other unsupported names return 400. — probes: no probe; expect: adjustment-needed (official-page)
- Docs read:
  - https://api-docs.deepseek.com/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/quick_start/pricing/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/api/create-chat-completion/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/api/list-models/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/guides/thinking_mode/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/guides/responses_api/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/guides/anthropic_api/ — fetched, 2026-10-20
  - https://api-docs.deepseek.com/quick_start/rate_limit — fetched, 2026-10-20
  - https://api-docs.deepseek.com/quick_start/error_codes — fetched, 2026-10-20
  - https://api-docs.deepseek.com/guides/kv_cache — fetched, 2026-10-20
  - https://api-docs.deepseek.com/guides/json_mode — fetched, 2026-10-20
- Notes: Read via curl through the agent proxy (WebFetch DNS fails). Price confirmed from pricing table: deepseek-flash peak (01:00-04:00 and 06:00-10:00 UTC Mon-Fri, excluding Chinese holidays) $0.30 in (cache miss) / $1.20 out, cache hit $0.006; off-peak half ($0.15 / $0.60 / $0.003). deepseek-v4-pro peak $1.32/$3.96. Round-1 'responses' and 'models_list' inferences are now confirmed by the Responses API and List Models pages. Round-1 legacy-name retirement conflict resolved: pages now only mention deepseek-v4-flash legacy names. Items marked 'not listed in the request schema' (n, seed, max_completion_tokens, developer role) are absences in the API reference, not explicit statements; verify with probes.

### Fireworks AI (`fireworks`)

- Tier / hosting: Priority 1 / hosted
- Secret: `FIREWORKS_API_KEY`
- Base URL: `https://api.fireworks.ai/inference/v1` (official-page)
- Auth: bearer via `Authorization` — 'Authenticate with your Fireworks API key as a bearer token' (Responses page); the OpenAI client takes the key as api_key.
- Models: chat `accounts/fireworks/models/nemotron-lightning-3p5-30b-a3b`, reasoning `accounts/fireworks/models/gpt-oss-120b` (official-page) — Ids use accounts/fireworks/models/<slug>; slugs are taken from the model links in the Serverless Pricing table (app.fireworks.ai/models/fireworks/<slug>), which lists NVIDIA Nemotron 3.5 Lightning 30B A3B at $0.05/$0.20 (cheapest named row) and OpenAI GPT OSS 120B at $0.15/$0.60. Whether Nemotron Lightning supports tools/reasoning was not checked. Other rows: GLM 5.3 Flash $0.15/$0.50, DeepSeek V4.1 Flash $0.30/$1.20. gpt-oss-120b is a Harmony model (effort low/medium/high only). Unlisted models are priced by size ($0.10 under 4B, $0.20 for 4B-16B, $0.90 above 16B).
- Price (chat model, USD per million tokens): input 0.05, output 0.2 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://api.fireworks.ai/inference/v1/messages` (official-page)
- Verification: **verified**
- Documented deviations:
  - max_tokens is lowered to fit the context window instead of erroring when prompt plus max_tokens exceeds it; context_length_exceeded_behavior=error restores OpenAI-style errors (default truncate). — probes: `output-cap.max-tokens`; expect: adjustment-needed (official-page)
  - max_completion_tokens is an alias of max_tokens and cannot be specified together with max_tokens. — probes: `output-cap.max-completion-tokens`; expect: compliant (official-page)
  - stop accepts up to 4 sequences. — probes: `ranges.stop-5`; expect: unsupported (official-page)
  - Streaming usage is included by default in a final chunk (the one with finish_reason set) rather than only when stream_options.include_usage is set; include_usage=false opts out. — probes: `streaming-usage.usage-default`, `streaming-usage.usage-included`; expect: adjustment-needed (official-page)
  - tool_choice accepts auto, none, any and required (any is an alias of required) or a named function; allowed_tools is also accepted. — probes: `tools.tool-required`; expect: compliant (official-page)
  - Reasoning is returned in message.reasoning_content (and delta.reasoning_content when streaming), separate from content. — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort accepts low, medium, high, xhigh, max, none, adaptive (MiniMax M3 only), booleans and integer token budgets (Fireworks extensions); Harmony models (GPT-OSS 120B/20B) accept only low, medium, high (default medium) and error on none/false/integers. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - On DeepSeek V4 models reasoning_effort 'medium' is silently promoted to 'high' and 'xhigh' to 'max'. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed; silent (official-page)
  - reasoning_effort 'auto' is dropped so the model default applies (NIM compatibility page). — probes: `reasoning.reasoning-effort`; expect: adjustment-needed; silent (official-page)
  - thinking and reasoning_effort cannot both be sent in the same request (validation error). — probes: no probe; expect: adjustment-needed (official-page)
  - Using response_format json_schema disables reasoning output; to get both, put the schema in the prompt and omit response_format. — probes: `structured-output.json-schema-strict`, `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - Models that cannot disable reasoning return 400 for reasoning_effort 'none' (and for enable_thinking:false mapped to none). — probes: no probe; expect: adjustment-needed (official-page)
  - Error bodies for inference use {"error": {"code": "NOT_FOUND", ...}, "request_id": ...}; codes documented: 400, 401, 402, 403, 404, 405, 408, 412, 413, 429, 500, 502, 503, 504, 520. — probes: `errors.error-shape`; expect: adjustment-needed (official-page)
  - Rate-limit response headers are X-Ratelimit-Limit-Tokens-Prompt, X-Ratelimit-Limit-Tokens-Cache-Adjusted-Prompt and X-Ratelimit-Limit-Tokens-Generated (limits only, tokens per minute); no remaining/reset headers are documented. Cached tokens are reported in the fireworks-cached-prompt-tokens header. — probes: `errors.rate-limit-headers`, `cached-tokens.cached-tokens`; expect: adjustment-needed (official-page)
  - NIM/vLLM request extras with no Chat Completions equivalent are stripped so requests are not rejected and do not change generation: guided_regex, guided_decoding_backend, stop_token_ids, include_stop_str_in_output, skip_special_tokens, spaces_between_special_tokens, best_of, use_beam_search, add_generation_prompt, continue_final_message, add_special_tokens, detokenize, allowed_token_ids, bad_words, include_reasoning, nvext. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - chat_template_kwargs keys other than thinking toggles (e.g. parallel_reasoning_mode) are dropped; enable_thinking:false maps to reasoning_effort none; truncate_prompt_tokens is renamed prompt_truncate_len. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Responses API is served at /inference/v1/responses; responses are stored by default and previous_response_id continues a conversation; store=false opts out. — probes: `responses.responses-basic`, `responses.responses-store`; expect: compliant (official-page)
  - Responses API does not support code_interpreter, file_search or image_generation tools. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support refusal content parts. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support GET /v1/responses/{id}/input_items. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support background and stream together. — probes: no probe; expect: unsupported (official-page)
  - Responses API supports only include value message.output_text.logprobs; other values are not supported. — probes: no probe; expect: unsupported (official-page)
  - defer_loading has no effect on the Responses API (deferred tools are exposed eagerly). — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Anthropic Messages endpoint: model must be a Fireworks model identifier, not an Anthropic model name. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: The anthropic-version header is not required and is ignored. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - Anthropic Messages endpoint: max_tokens is required and must be > 0 (omitting it returns 400 invalid_request_error). — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: inference_geo is deprecated in favor of data residency and should be removed from requests. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: thinking.budget_tokens is converted to a reasoning_effort band (>=10000 high, 5000-9999 medium, 1024-4999 low); output_config.effort wins when both are present; thinking.type disabled sends reasoning_effort none. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: Enforced 400s: thinking.budget_tokens required (>=1024) when thinking is enabled, max_tokens must exceed budget_tokens, enabled thinking cannot combine with forced tool_choice any/tool, and assistant turns cannot be pre-filled while thinking is enabled or adaptive. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: document content blocks and image blocks with source.type 'file' are rejected. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: Only POST /v1/messages is served; /v1/messages/count_tokens returns 404 and /v1/messages/batches is unavailable. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: Anthropic server tools (code execution, memory, web fetch, web search) are not supported; declaring web_search_20250305 returns 400. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: Server-tool metadata fields (caller, container) and server_tool_use usage tracking are not supported. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: Tool schema fields eager_input_streaming, cache_control, allowed_callers and input_examples are not supported. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: output_config.speed is not supported yet. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: Error bodies use the Anthropic envelope; status codes follow Anthropic conventions (422 reported as 400, billing 402, oversized 413); unknown model or unrouted path returns the standard Fireworks error body instead. — probes: no probe; expect: adjustment-needed (official-page)
  - Anthropic Messages endpoint: input_tokens excludes cache-read tokens (reported as cache_read_input_tokens) and cache_creation_input_tokens is always 0. — probes: no probe; expect: adjustment-needed (official-page)
- Docs read:
  - https://docs.fireworks.ai/tools-sdks/openai-compatibility — fetched, 2026-10-20
  - https://docs.fireworks.ai/tools-sdks/nim-compatibility — fetched, 2026-10-20
  - https://docs.fireworks.ai/tools-sdks/anthropic-compatibility — fetched, 2026-10-20
  - https://docs.fireworks.ai/guides/response-api — fetched, 2026-10-20
  - https://docs.fireworks.ai/guides/reasoning — fetched, 2026-10-20
  - https://docs.fireworks.ai/api-reference/post-chatcompletions.md — fetched, 2026-10-20
  - https://docs.fireworks.ai/serverless/pricing.md — fetched, 2026-10-20
  - https://docs.fireworks.ai/serverless/overview.md — fetched, 2026-10-20
  - https://docs.fireworks.ai/serverless/rate-limits.md — fetched, 2026-10-20
  - https://docs.fireworks.ai/guides/inference-error-codes — fetched, 2026-10-20
  - https://docs.fireworks.ai/structured-responses/structured-response-formatting — fetched, 2026-10-20
- Notes: Read via curl through the agent proxy (WebFetch DNS fails); fireworks.ai/pricing was unreachable (403 on CONNECT) so prices come from docs.fireworks.ai/serverless/pricing. The Anthropic SDK base URL is https://api.fireworks.ai/inference (no /v1; SDK appends /v1/messages). Rate limits are adaptive (429/503; Priority tier reduces 503s). Round-1 claims corrected: the medium->high / xhigh->max promotion applies to DeepSeek V4 models only (chat completions API reference), not to Harmony models; the stop-sequence changelog claim was dropped (the API reference says up to 4 sequences). models_list not found in pages read. Docs also list max-tokens/context behavior and prompt caching on by default for serverless (replica-local; x-session-affinity header).

### Groq (`groq`)

- Tier / hosting: Priority 1 / hosted
- Secret: `GROQ_API_KEY`
- Base URL: `https://api.groq.com/openai/v1` (official-page)
- Auth: bearer via `Authorization` — Examples send 'Authorization: Bearer $GROQ_API_KEY'; the OpenAI client takes the Groq key as api_key.
- Models: chat `openai/gpt-oss-20b`, reasoning `openai/gpt-oss-20b` (official-page) — gpt-oss-20b is the cheapest production model with a public price on the models page ($0.075/$0.30). llama-3.1-8b-instant shows 'Contact Sales' (Enterprise) there and the deprecations page lists it for shutdown on 08/16/26 with openai/gpt-oss-20b as replacement. gpt-oss-20b is on the reasoning supported-models list; reasoning_effort low/medium/high; reasoning returned in message.reasoning by default. Other reasoning models: openai/gpt-oss-120b, qwen/qwen3.8-27b.
- Price (chat model, USD per million tokens): input 0.075, output 0.3 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (unspecified)
- Verification: **verified**
- Documented deviations:
  - logprobs is listed as unsupported and returns a 400 error if supplied; the API reference says it is not yet supported by any model. — probes: `ranges.logprobs`; expect: unsupported (official-page)
  - top_logprobs is listed as unsupported and returns a 400 error if supplied; the API reference says it is not yet supported by any model. — probes: `ranges.logprobs`; expect: unsupported (official-page)
  - logit_bias is listed as unsupported and returns a 400 error if supplied. — probes: no probe; expect: unsupported (official-page)
  - messages[].name is listed as unsupported and returns a 400 error if supplied. — probes: `roles.name-field`; expect: unsupported (official-page)
  - n must be 1: the API reference gives range 1-1 and says other values return a 400 response. — probes: `ranges.n-2`; expect: unsupported (official-page)
  - frequency_penalty and presence_penalty are documented as not yet supported by any model (API reference); the docs do not say whether a request is rejected. — probes: no probe; expect: unsupported (official-page)
  - store and metadata are documented as not currently supported on chat completions. — probes: no probe; expect: unsupported (official-page)
  - A temperature of 0 is converted to 1e-8; the docs suggest a float32 > 0 and <= 2 if issues arise. — probes: no probe; expect: adjustment-needed (official-page)
  - seed is best effort: determinism is not guaranteed and system_fingerprint should be monitored for backend changes. — probes: `ranges.seed`; expect: compliant (official-page)
  - stop accepts up to 4 sequences. — probes: `ranges.stop-5`; expect: unsupported (official-page)
  - max_completion_tokens is the documented output cap parameter (also used in reasoning-page examples). — probes: `output-cap.max-completion-tokens`; expect: compliant (official-page)
  - max_tokens is still accepted but marked Deprecated in favor of max_completion_tokens. — probes: `output-cap.max-tokens`; expect: compliant (official-page)
  - stream_options.include_usage adds an extra final chunk before [DONE] with an empty choices array and the usage totals; other chunks carry usage null when it is set. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - tool_choice accepts none, auto, required or a named function; at most 128 functions per request. — probes: `tools.tool-required`; expect: compliant (official-page)
  - For gpt-oss models reasoning_format is not supported; reasoning is returned by default in a message.reasoning field and include_reasoning=false suppresses it. — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort on gpt-oss-20b/120b accepts low, medium or high (default medium); values outside a model's supported set are rejected with a 400. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - include_reasoning is mutually exclusive with reasoning_format. — probes: no probe; expect: adjustment-needed (official-page)
  - For non-gpt-oss reasoning models, reasoning_format raw combined with JSON mode or tool use returns a 400; reasoning_format must be parsed or hidden in those modes (it defaults to raw or parsed per the docs). — probes: no probe; expect: adjustment-needed (official-page)
  - response_format json_schema with strict:true (constrained decoding) is available only on select models, including openai/gpt-oss-20b and openai/gpt-oss-120b; strict mode requires all fields required and additionalProperties false. — probes: `structured-output.json-schema-strict`; expect: compliant (official-page)
  - Prompt caching is automatic and only supported on openai/gpt-oss-20b, gpt-oss-120b and gpt-oss-safeguard-20b; cached input tokens get a 50% discount, hits are best effort, and usage reports cached_tokens. — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Rate-limit headers: x-ratelimit-limit-requests and -remaining-requests/-reset-requests always refer to requests per DAY, while the -tokens headers refer to tokens per minute; reset values are durations such as 2m59.56s; retry-after (seconds) is only set on a 429. — probes: `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - Text Completions is listed under 'Currently Unsupported OpenAI Features' on the compatibility page. — probes: no probe; expect: unsupported (official-page)
  - Audio transcription and translation do not support the vtt and srt response formats. — probes: no probe; expect: unsupported (official-page)
  - Responses API is in beta and does not support stateful conversations yet; history must be resent in input. — probes: `responses.responses-store`; expect: unsupported (official-page)
  - Responses API is documented as fully compatible for text and image input with function calling. — probes: `responses.responses-basic`; expect: compliant (official-page)
  - Responses API does not support previous_response_id. — probes: `responses.responses-store`; expect: unsupported (official-page)
  - Responses API does not support store. — probes: `responses.responses-store`; expect: unsupported (official-page)
  - Responses API does not support truncation. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support include. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support safety_identifier. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support prompt_cache_key. — probes: no probe; expect: unsupported (official-page)
  - Responses API does not support prompt (reusable prompts). — probes: no probe; expect: unsupported (official-page)
- Docs read:
  - https://console.groq.com/docs/openai — fetched, 2026-10-20
  - https://console.groq.com/docs/models — fetched, 2026-10-20
  - https://console.groq.com/docs/reasoning — fetched, 2026-10-20
  - https://console.groq.com/docs/rate-limits — fetched, 2026-10-20
  - https://console.groq.com/docs/responses-api — fetched, 2026-10-20
  - https://console.groq.com/docs/structured-outputs — fetched, 2026-10-20
  - https://console.groq.com/docs/prompt-caching — fetched, 2026-10-20
  - https://console.groq.com/docs/deprecations — fetched, 2026-10-20
  - https://console.groq.com/docs/api-reference — fetched, 2026-10-20
- Notes: All pages read via curl through the agent proxy because WebFetch fails DNS for these hosts; content is the provider's own docs. Free plan limits for gpt-oss-20b: 30 RPM, 1K RPD, 8K TPM, 200K TPD (rate-limits page); Developer plan 1K RPM / 250K TPM. No Anthropic Messages endpoint found in the pages read. Error response shape and model-identity behavior not documented in the pages read. Round-1 claim that Responses is 'same base URL' confirmed; round-1 claim 'N must be 1' confirmed.

### Hugging Face Inference Providers (`huggingface`)

- Tier / hosting: Priority 1 / hosted
- Secret: `HUGGINGFACE_API_KEY`
- Base URL: `https://router.huggingface.co/v1` (official-page)
- Auth: bearer via `Authorization` — Bearer HF user access token (hf_...), fine-grained with 'Make calls to Inference Providers' permission. Docs examples use env var HF_TOKEN. Optional X-HF-Bill-To header bills a Team/Enterprise org.
- Models: chat `meta-llama/Llama-3.1-8B-Instruct`, reasoning `openai/gpt-oss-120b` (official-page) — Both ids appear in the live GET https://router.huggingface.co/v1/models list (checked 2026-10-20: Llama-3.1-8B-Instruct served by novita/nscale/featherless-ai/deepinfra, cheapest $0.02/$0.05 via novita and deepinfra; gpt-oss-120b served by 11 providers from $0.037/$0.17 deepinfra up to $0.35/$0.75 cerebras). Provider choice is by suffix :fastest (default), :cheapest, :preferred or a provider name e.g. openai/gpt-oss-120b:groq. Qwen/Qwen3-4B-Thinking-2507 (nscale $0.01/$0.03) is a small reasoning model. Llama-3.1-8B providers report supports_tools false.
- Price (chat model, USD per million tokens): input 0.02, output 0.05 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://router.huggingface.co` (official-page)
- Verification: **verified**
- Documented deviations:
  - The router proxies many providers; the exact HTTP request may vary between providers as each has its own API requirements and response formats. Provider is chosen dynamically (:fastest default) with automatic failover when the primary provider is flagged unavailable. — probes: `model-identity.model-identity`; expect: adjustment-needed (official-page)
  - Chat completion API specification documents max_tokens only (no max_completion_tokens, no n parameter); stop is up to 4 sequences; top_logprobs 0-5; tool_choice auto/none/required/function; non-standard tool_prompt. — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`, `ranges.n-2`, `ranges.stop-5`, `tools.tool-required`; expect: adjustment-needed (official-page)
  - reasoning_effort is optional; common values none, minimal, low, medium, high, xhigh; support and defaults are provider and model dependent. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - response_format accepts text, json_object and json_schema (name, description, schema, strict). — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (official-page)
  - stream_options.include_usage: extra chunk before [DONE] with usage and an empty choices array; other chunks carry usage null. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - Message content parts documented are text and image_url only; messages carry an optional name; the schema lists no developer role and no reasoning field on messages. — probes: `content-shape.content-parts`, `images.image-part`, `roles.name-field`, `roles.developer-role`; expect: adjustment-needed (official-page)
  - temperature 0-2, logprobs, seed are documented request parameters. — probes: `ranges.temperature-2`, `ranges.logprobs`, `ranges.seed`; expect: compliant (official-page)
  - Responses API (beta) at https://router.huggingface.co/v1/responses (OpenAI SDK client.responses.create, model ids with optional :provider suffix, reasoning={'effort': low|medium|high}); the guide states all Inference Providers chat completion models should be compatible; store/previous_response_id behavior is not documented in the guide. — probes: `responses.responses-basic`; expect: compliant (official-page)
  - Chat Completion only on the OpenAI-compatible endpoint (no embeddings, images, speech); GET /v1/models returns models across providers with per-provider pricing, context length, supports_tools, supports_structured_output, latency and throughput. — probes: no probe; expect: compliant (official-page)
- Docs read:
  - https://huggingface.co/docs/inference-providers/index.md — fetched, 2026-10-20
  - https://huggingface.co/docs/inference-providers/tasks/chat-completion.md — fetched, 2026-10-20
  - https://huggingface.co/docs/inference-providers/guides/responses-api.md — fetched, 2026-10-20
  - https://huggingface.co/docs/inference-providers/pricing.md — fetched, 2026-10-20
  - https://huggingface.co/docs/inference-providers/integrations/claude-code.md — fetched, 2026-10-20
  - https://router.huggingface.co/v1/models — fetched, 2026-10-20
- Notes: Docs call it a drop-in replacement for the OpenAI chat completions API. Pricing: pay-as-you-go passthrough with no HF markup; free users have no included credits (credit purchase required), PRO $2/month, Team/Enterprise $2 per seat. Anthropic-compatible: Claude Code integration sets ANTHROPIC_BASE_URL=https://router.huggingface.co with the HF token (SDK appends /v1/messages); request schema not read. Price shown is the cheapest live provider for the chat model, not a fixed price. Because provider selection is dynamic, pin a provider suffix (e.g. :deepinfra, :novita) for repeatable CI probes. Secret name follows the brief's rule (HUGGINGFACE_API_KEY); docs use HF_TOKEN. No error-shape or rate-limit-header facts in the fetched pages.

### Mistral La Plateforme (`mistral`)

- Tier / hosting: Priority 1 / hosted
- Secret: `MISTRAL_API_KEY`
- Base URL: `https://api.mistral.ai/v1` (official-page)
- Auth: bearer via `Authorization` — curl examples and the error glossary specify 'Authorization: Bearer YOUR_API_KEY'.
- Models: chat `ministral-3b-latest`, reasoning `mistral-small-latest` (official-page) — Model cards: Ministral 3 3B (api names ministral-3b-2512, alias ministral-3b-latest) $0.1/$0.1 per M tokens, 256k context, function calling and structured outputs; Mistral Small 4 (mistral-small-2603, alias mistral-small-latest) $0.15/$0.6, 256k context, adjustable reasoning via reasoning_effort. The reasoning page lists reasoning_effort support for mistral-small-latest, mistral-medium-3-5, mistral-large-4-0 and zai-glm-5-3 (low/high/max, no none). Magistral models are deprecated.
- Price (chat model, USD per million tokens): input 0.1, output 0.1 (official-page)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (unspecified)
- Verification: **verified**
- Documented deviations:
  - The Migration guide says the Mistral Chat Completions API follows the same request structure as OpenAI and documents pointing an OpenAI client at https://api.mistral.ai/v1 with only base URL and model name changed. — probes: no probe; expect: compliant (official-page)
  - The seed parameter is named random_seed (integer or null) in the documented request schema. — probes: `ranges.seed`; expect: adjustment-needed (official-page)
  - max_tokens is the documented output cap; max_completion_tokens is not in the documented request schema. — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - Documented message roles are system, user, assistant and tool; developer is not listed. — probes: `roles.developer-role`; expect: adjustment-needed (official-page)
  - tool_choice accepts auto, none, any and required (any/required force a tool call without guaranteeing which tool) or a named function; at most 128 tools per request; parallel_tool_calls defaults true. — probes: `tools.tool-required`; expect: compliant (official-page)
  - n and logprobs/top_logprobs are in the request schema (n: input tokens billed once). — probes: `ranges.n-2`, `ranges.logprobs`; expect: compliant (official-page)
  - Temperature is recommended between 0.0 and 0.7 with a model-dependent default; the error glossary gives 'temperature outside 0 to 1 range' as an example of an unsupported parameter value returning 400. — probes: `ranges.temperature-2`; expect: adjustment-needed (official-page)
  - With reasoning_effort high, message.content is a list of chunks (a thinking chunk with a thinking list of text chunks, plus text chunks) instead of a plain string; with none it is a plain string. zai-glm-5-3 always returns a chunk list. — probes: `reasoning.reasoning-shape`, `content-shape.content-parts`; expect: adjustment-needed (official-page)
  - Multi-turn reasoning requires replaying the full assistant message including the thinking chunk; stripping it degrades quality. — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - reasoning_effort enum is none, minimal, low, medium, high, xhigh in the schema, but support is per model (mistral-small-latest, mistral-medium-3-5, mistral-large-4-0; zai-glm-5-3 takes low/high/max and not none). — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - response_format supports text, json_object and json_schema (schema-following JSON mode); JSON mode needs the word JSON in the prompt and does not guarantee a specific schema. — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (official-page)
  - stream_options.include_usage must be explicitly set to receive token usage in stream events (Known limitations); the chat request schema in the API reference does not list stream_options. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - prompt_cache_key is supported and cached tokens are billed at 10% of the input price; the usage field names for cached tokens are not documented on the pages read. — probes: no probe; expect: adjustment-needed (official-page)
  - Error glossary uses 400 for malformed requests and 422 for validation errors (wrong type, enum value not allowed); 402 for billing blocks; 429 on rate limits. — probes: `errors.error-shape`; expect: adjustment-needed (official-page)
  - Only the X-RateLimit-Remaining response header is named in the docs; limits are per organization, requests per second and tokens per minute enforced independently. — probes: `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - Mistral-specific extras in the request schema: prompt_mode, safe_prompt, random_seed, min_tokens, top_k, repetition_penalty, prediction, guardrails, service_tier (auto, standard_only). — probes: no probe; expect: adjustment-needed (official-page)
  - Images: max 20 MB each; PNG, JPG, JPEG, GIF, WEBP; vision availability depends on the model. — probes: `images.image-part`; expect: compliant (official-page)
- Docs read:
  - https://docs.mistral.ai/api — fetched, 2026-10-20
  - https://docs.mistral.ai/studio/conversations/reasoning — fetched, 2026-10-20
  - https://docs.mistral.ai/models — fetched, 2026-10-20
  - https://docs.mistral.ai/models/mistral-small-4-0-26-03 — fetched, 2026-10-20
  - https://docs.mistral.ai/models/ministral-3-3b-25-12 — fetched, 2026-10-20
  - https://docs.mistral.ai/resources/migration-guides — fetched, 2026-10-20
  - https://docs.mistral.ai/resources/known-limitations — fetched, 2026-10-20
  - https://docs.mistral.ai/resources/error-glossary — fetched, 2026-10-20
  - https://mistral.ai/pricing/api/ — not-reached, 2026-10-20
- Notes: Read via curl through the agent proxy (WebFetch DNS fails); prices come from the docs model cards because mistral.ai/pricing was not fetched. Mistral is an own-API provider that documents OpenAI-client use via base URL only, not a parameter-level compatibility page. responses is null: no Responses endpoint appears in the API reference navigation (Mistral has Beta Conversations instead). models_list true from the 'List Available Models API' reference and the Models section. Absences marked in deviations (max_completion_tokens, developer role) are not in the request schema, not explicit statements. Round-1 'Mistral Small 4 / mistral-small-latest' and reasoning_effort claims confirmed; round-1 cheapest chat model changed to ministral-3b-latest ($0.1/$0.1).

### Moonshot AI (Kimi) (`moonshot`)

- Tier / hosting: Priority 1 / hosted
- Secret: `MOONSHOT_API_KEY`
- Base URL: `https://api.moonshot.ai/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer $MOONSHOT_API_KEY. Keys from platform.kimi.ai are independent from keys of other regional Kimi platforms; mixing returns 401. No free tier: minimum recharge $1 to start (cumulative $5 recharge earns a $5 voucher); kimi-k3 is unlocked after a successful top-up (min $1). Rate limits are tiered by cumulative recharge (RPM/TPM/TPD/concurrency, shared across models, enforced per user not per key); the tier table is JS-rendered and was not readable.
- Models: chat `kimi-k2.6`, reasoning `kimi-k3` (official-page) — Current models on the Model List page: kimi-k3, kimi-k2.7-code, kimi-k2.7-code-highspeed, kimi-k2.6. kimi-k2.5, moonshot-v1-*, kimi-k2* were discontinued (Aug 31 / May 25 2026). kimi-k2.6 is the cheapest current model ($0.95 in / $4.00 out per 1M); it thinks by default and thinking can be disabled with extra_body {"thinking": {"type": "disabled"}}. kimi-k3 ($3.00 in / $15.00 out) always reasons, takes top-level reasoning_effort low/high/max (default max). kimi-k2.7-code always thinks and cannot disable it.
- Price (chat model, USD per million tokens): input 0.95, output 4.0 (official-page: pricing table for kimi-k2.6, cache-miss input; cached input $0.16. kimi-k3: input $3.00, cached $0.30, cache write 5min $3.00 / 1h $6.00, output $15.00. kimi-k2.7-code: $0.95 in / $4.00 out, cached $0.19.)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://api.moonshot.ai/anthropic (SDK base_url; POST /anthropic/v1/messages)` (official-page)
- Verification: **verified**
- Documented deviations:
  - temperature range is [0, 1] (OpenAI [0, 2]); kimi-k2.6 fixed at 1.0 (thinking) / 0.6 (non-thinking), kimi-k2.7-code and kimi-k3 fixed at 1.0; any other value returns an error; top_p fixed 0.95 — probes: `ranges.temperature-2`; expect: unsupported (official-page)
  - n is fixed at 1 for kimi-k3, kimi-k2.7-code and kimi-k2.6; temperature 0 (or near 0) allows one choice only and temperature 0 with n>1 returns invalid_request_error — probes: `ranges.n-2`; expect: unsupported (official-page)
  - presence_penalty and frequency_penalty fixed at 0; passing any other value returns an error; the deprecated functions/function_call parameters are not supported — probes: no probe; expect: unsupported (official-page)
  - max_tokens is deprecated in favour of max_completion_tokens (kimi-k3 default 131072, max 1048576); reasoning_content tokens count toward the limit — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: compliant (official-page)
  - stop: up to 5 strings, each at most 32 bytes — probes: `ranges.stop-5`; expect: compliant (official-page)
  - logprobs (boolean) and top_logprobs (0-20) are listed in the chat request schema — probes: `ranges.logprobs`; expect: compliant (official-page)
  - Message roles are system, user, assistant, tool (no developer); optional name field per message; non-standard partial:true on the last assistant message enables Partial Mode — probes: `roles.developer-role`, `roles.name-field`; expect: unsupported (official-page)
  - content may be a string or an array of text, image_url, video_url parts; image_url/video_url carry a base64 data URL or an ms://<file_id> reference (remote http URLs are not documented) — probes: `content-shape.content-parts`, `images.image-part`; expect: adjustment-needed (official-page)
  - tool_choice: kimi-k3 supports auto/none/required/named function; kimi-k2.6 and kimi-k2.7-code do not support 'required' and return an error — probes: `tools.tool-required`; expect: unsupported (official-page)
  - response_format supports text, json_object and json_schema (name + schema, strict); kimi-k2.6 is unstable with complex schemas ($ref may return a code block, oneOf may be ignored) — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (official-page)
  - stream_options.include_usage adds a final chunk with empty choices and usage; in addition Kimi places usage in the final chunk of each choice (choice.usage); intermediate chunks carry usage: null — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - usage.cached_tokens and usage.prompt_tokens_details.cached_tokens (plus cache_write_tokens) are returned; automatic prefix caching with prompt_cache_key / prompt_cache_options.ttl (5m or 1h) request fields — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Reasoning returned in non-standard reasoning_content (message and delta; always streamed before content); kimi-k2.6 uses extra_body thinking {type: enabled|disabled, keep}; kimi-k3 always thinks — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort ('low', 'high', 'max', default max) is only supported by kimi-k3; kimi-k2.6 and kimi-k2.7-code do not support it — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - Complete assistant messages including reasoning_content must be passed back unchanged (Preserved Thinking always on for kimi-k3 and kimi-k2.7-code; thinking.keep='all' for kimi-k2.6) — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - /v1/responses is documented for kimi-k3 only; its request schema has input, instructions, stream, max_output_tokens, reasoning.effort, text.format, tools, tool_choice, include, prompt_cache_* and no store or previous_response_id fields — probes: `responses.responses-basic`; expect: compliant (official-page)
  - Responses request schema lists no store/previous_response_id (absence in schema, not an explicit 'unsupported' statement) — probes: `responses.responses-store`; expect: unsupported (official-page)
  - Errors use {error: {type, message}} with types such as invalid_request_error, rate_limit_reached_error, engine_overloaded_error; 429 engine_overloaded advises honoring Retry-After; X-RateLimit-Limit/Remaining/Reset are documented only for the web-search endpoints — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
- Docs read:
  - https://platform.kimi.ai/docs/api/overview — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/guide/migrating-from-openai-to-kimi — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/api/chat — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/openapi.json — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/api/models-overview.md — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/models.md — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/pricing/chat — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/pricing/limits.md — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/api/errors.md — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/guide/use-thinking-models.md — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/guide/response_format.md — fetched, 2026-10-20
  - https://platform.kimi.ai/docs/introduction.md — fetched, 2026-10-20
- Notes: Read via curl through the sandbox proxy (WebFetch fails DNS). platform.moonshot.ai was not tried via curl; platform.kimi.ai is the live docs host. Parameter facts come from the OpenAPI schema (kimi-k3 / k2.7-code / k2.6 request variants) plus the Model Parameter Reference page, whose comparison table is JS-rendered (read the prose below it). seed, n, top_p, penalties are not in the chat request schema (prose says fixed values). Round-1 China base URL and third-party prices removed (not on official pages).

### NVIDIA NIM (build.nvidia.com hosted) (`nvidia`)

- Tier / hosting: Priority 1 / hosted
- Secret: `NVIDIA_API_KEY`
- Base URL: `https://integrate.api.nvidia.com/v1` (official-page)
- Auth: bearer via `Authorization` — API key obtained via 'Get API Key' on a build.nvidia.com model page; sign-up makes you a member of the NVIDIA Developer Program (quickstart). Chat endpoint POST https://integrate.api.nvidia.com/v1/chat/completions. Free-tier credit/rate-limit terms were not on the pages read.
- Models: chat `meta/llama-3.1-8b-instruct`, reasoning `openai/gpt-oss-20b` (official-page) — Both ids are listed on the LLM APIs reference index today (also nvidia/nemotron-nano-9b-v2, nvidia/nemotron-3-nano-30b-a3b, microsoft/phi-4-mini-flash-reasoning, qwen/qwen3-next-80b-a3b-thinking, meta/llama-3.2-1b/3b-instruct, z-ai/glm-5.3-flash, moonshotai/kimi-k3). The llama-3.1-8b-instruct reference page defaults model to meta/llama-3.1-8b-instruct; the gpt-oss-20b page defaults to openai/gpt-oss-20b and adds reasoning_effort low/medium/high (default medium). Cheapest not determinable: no per-token prices on these pages.
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not mentioned on pages read)
- Verification: **partial**
- Documented deviations:
  - temperature range 0 to 1 (default 0.2 for llama-3.1-8b-instruct, 0.6 for gpt-oss-20b); top_p up to 1 (default 0.7); not recommended to change both — probes: `ranges.temperature-2`; expect: unsupported (official-page)
  - max_tokens is the only output cap listed: range 1 to 4096 (default 1024 for llama-3.1-8b, 4096 for gpt-oss-20b); max_completion_tokens is not listed — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - Messages: 'The roles of the messages must be alternating between user and assistant. The last input message should have role user. A message with the system role is optional, and must be the very first message if it is present' — probes: `roles.two-system`, `roles.consecutive-user`, `roles.developer-role`; expect: unsupported (official-page)
  - frequency_penalty and presence_penalty range -2 to 2 (default 0); stop accepts a string or list of strings with no limit stated — probes: `ranges.stop-5`; expect: compliant (official-page)
  - tools and tool_choice (string or named-tool object, 'Controls which (if any) tool is called by the model') are listed on llama-3.1-8b-instruct; accepted values not enumerated — probes: `tools.tool-required`; expect: compliant (official-page)
  - gpt-oss-20b accepts reasoning_effort low/medium/high (default medium); reasoning output field name is not documented on the page — probes: `reasoning.reasoning-effort`; expect: compliant (official-page)
  - stream is supported (SSE data-only events ending with data: [DONE]); stream_options, response_format, n, seed, logprobs are not listed on the two reference pages read — probes: `streaming-usage.usage-included`; expect: adjustment-needed (official-page)
- Docs read:
  - https://docs.api.nvidia.com/nim/reference/llm-apis — fetched, 2026-10-20
  - https://docs.api.nvidia.com/nim/reference/meta-llama-3_1-8b-infer — fetched, 2026-10-20
  - https://docs.api.nvidia.com/nim/reference/openai-gpt-oss-20b-infer — fetched, 2026-10-20
  - https://docs.api.nvidia.com/nim/docs/api-quickstart — fetched, 2026-10-20
  - https://docs.nvidia.com/nim/large-language-models/latest/api-reference.html — not-reached, 2026-10-20
- Notes: Read via curl through the sandbox proxy (WebFetch fails DNS). The per-model API reference pages list parameters but each model has its own list; parameters not listed (response_format, stream_options, n, seed, logprobs, tool details) cannot be asserted either way. docs.nvidia.com returned a 99-byte stub and integrate.api.nvidia.com is blocked by the sandbox proxy, so GET /v1/models and live behavior were not checked. Round-1 claims about nvext guided decoding, gpt-oss sequential tool calls, nvapi- key prefix and free-credit amounts were not found on these pages and were removed.

### Perplexity (Orchestrator API) (`perplexity`)

- Tier / hosting: Priority 1 / hosted
- Secret: `PERPLEXITY_API_KEY`
- Base URL: `https://api.perplexity.ai/router/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <PERPLEXITY_API_KEY>. The Orchestrator API is in private preview (email api@perplexity.ai for access), so a standard key may not be enough.
- Models: chat `perplexity/glm-5.3-flash`, reasoning `perplexity/kimi-k3` (official-page) — Both ids are on the Orchestrator Models & Pricing page (creator/model-name slugs; the catalog is also the allowlist, unknown model returns 400). glm-5.3-flash is the cheapest listed ($0.15/$0.50 per Mtok) and the Agent API models page calls GLM 5.3 / 5.3 Flash 'Z.AI reasoning models', so it can double as the cheap reasoning model; kimi-k3 ($3/$15) is the documented flagship reasoning model and accepts minimal/low/medium/high/xhigh/max effort. Other catalog ids: perplexity/glm-5.3, perplexity/deepseek-v4-pro-0813. Legacy Sonar models (sonar, sonar-reasoning-pro) are not in the current docs; Sonar Chat Completions support ended 2026-09-27.
- Price (chat model, USD per million tokens): input 0.15, output 0.5 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://api.perplexity.ai/router/v1/messages` (official-page)
- Verification: **verified**
- Documented deviations:
  - Orchestrator API accepts the OpenAI Chat Completions schema with a defined subset of parameters; a request using an unsupported parameter fails with HTTP 400 naming the parameter. — probes: no probe; expect: adjustment-needed (official-page)
  - Rejected with a 400: seed, logit_bias, top_logprobs, functions, function_call, modalities, audio, prediction, web_search_options, moderation, verbosity, stream_options.include_obfuscation=true, plus any unrecognized top-level field. — probes: `ranges.seed`; expect: unsupported (official-page)
  - Accepted only at their default values: n (1), logprobs (false), store (false), presence_penalty (0), frequency_penalty (0). — probes: `ranges.n-2`, `ranges.logprobs`; expect: unsupported (official-page)
  - Accepted but not forwarded to the model: user, safety_identifier, metadata. — probes: no probe; expect: compliant; silent (official-page)
  - Honored: model, messages, stream, stream_options.include_usage, max_completion_tokens, max_tokens (legacy alias), temperature, top_p, stop (up to 4 sequences), reasoning_effort, service_tier (auto/default/flex/priority), response_format (text/json_schema/json_object), tools, tool_choice (incl. allowed_tools), parallel_tool_calls, prompt_cache_key/options/retention. — probes: `output-cap.max-completion-tokens`, `output-cap.max-tokens`, `structured-output.json-object`, `ranges.temperature-2`; expect: compliant (official-page)
  - stop accepts up to 4 sequences. — probes: `ranges.stop-5`; expect: unsupported (official-page)
  - response_format.json_schema always runs in strict mode. — probes: `structured-output.json-schema-strict`; expect: compliant (official-page)
  - Message schema includes a developer role; the leading system or developer message is applied as the system prompt. — probes: `roles.developer-role`; expect: compliant (official-page)
  - Function tools require a description. — probes: `tools.tool-required`; expect: adjustment-needed (official-page)
  - stream_options.include_usage: usage arrives in a final chunk (empty choices) before data: [DONE]; usage only present when requested. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - Usage carries prompt_tokens_details.cached_tokens. — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - reasoning_effort accepts none, minimal, low, medium, high, xhigh, max; when omitted the provider default for that model applies and defaults vary by model. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - Assistant messages and streamed deltas can carry reasoning_content (not a 'reasoning' field); replaying it in an assistant message preserves reasoning context across requests. — probes: `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Errors use the OpenAI envelope {error:{code,message,param,type}}; type is invalid_request_error (4xx), rate_limit_error (429, rate limited or model overloaded) or server_error (5xx); Retry-After accompanies 429. — probes: `errors.error-shape`; expect: compliant (official-page)
  - Orchestrator rate limiting: a 429 (rate limited or model overloaded) carries a Retry-After header; x-ratelimit-* headers are documented only for the Agent API (success responses: x-ratelimit-limit/remaining/used/reset; its 429s carry Retry-After and no x-ratelimit-*), not for the Orchestrator. — probes: `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - Response echoes the requested model id; billing always uses that model's published rates regardless of how the request was served. — probes: `model-identity.model-identity`; expect: compliant (official-page)
  - Responses endpoint (/router/v1/responses) is stateless: store:true, background:true, non-null previous_response_id, max_tool_calls, top_logprobs, truncation:auto, file/video input, item references, text.format json_object and text.format.strict:false are rejected with 400. — probes: `responses.responses-basic`, `responses.responses-store`; expect: unsupported (official-page)
  - Sonar Chat Completions support ended 2026-09-27 (sync/stream requests are being reformulated as Agent API requests; async Sonar no longer supported). The Agent API (base https://api.perplexity.ai/v1, POST /v1/agent aliased as /v1/responses) is Responses-style only and is a different product from the Orchestrator. — probes: no probe; expect: adjustment-needed (official-page)
  - Agent API: for GPT-5, o1 and o3 families (e.g. openai/gpt-5.6-sol) temperature and top_p are silently ignored. — probes: no probe; expect: adjustment-needed; silent (official-page)
- Docs read:
  - https://docs.perplexity.ai/docs/router/quickstart.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/router/models.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/api-reference/gateway-chat-completions-post.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/api-reference/gateway-responses-post.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/api-reference/gateway-models-get.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/getting-started/pricing.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/admin/rate-limits-usage-tiers.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/agent-api/openai-compatibility.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/agent-api/models.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/sonar/openai-compatibility.md — fetched, 2026-10-20
  - https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar/how-to.md — fetched, 2026-10-20
- Notes: Round 1 was wrong: Sonar chat completions (api.perplexity.ai/chat/completions, sonar/sonar-reasoning-pro) is superseded. The OpenAI Chat Completions-compatible surface is now the Orchestrator API at /router/v1, but it is a PRIVATE PREVIEW (access request required), so it is not CI-friendly without approval. The Agent API (/v1) is OpenAI Responses-compatible (model ids like openai/gpt-5.6-sol; preset param extension; per-tool fees) and has no chat/completions endpoint per the docs. Prices from Orchestrator models page; per-token only, no per-request fees; 429s are not billed; reasoning tokens billed at output rate. Orchestrator rate-limit numbers are not published on the rate-limits page (the tier tables there are for the Agent API: Tier 0 = 1 QPS). docs.perplexity.ai is unreachable via WebFetch (DNS) but reachable with curl through the proxy; .md variants of pages were read. Sonar docs page at /docs/sonar/openai-compatibility now serves the Sonar-to-Agent migration overview.

### Together AI (`together`)

- Tier / hosting: Priority 1 / hosted
- Secret: `TOGETHER_API_KEY`
- Base URL: `https://api.together.ai/v1` (official-page)
- Auth: bearer via `Authorization` — Examples send 'Authorization: Bearer $TOGETHER_API_KEY'.
- Models: chat `Qwen/Qwen3.5-9B`, reasoning `openai/gpt-oss-120b` (official-page) — Both ids are in the serverless chat table. Qwen3.5 9B ($0.17/$0.25, function calling and structured outputs yes, also listed as a vision model) is the cheapest non-experimental paid chat row with tools. Cheaper rows exist but are marginal: together/Tev1-4B-experimental ($0.042 in / free out, no function calling or structured outputs) and Prism-ML/Ternary-Bonsai-27B (Free). openai/gpt-oss-120b ($0.15/$0.60) supports reasoning_effort low/medium/high ('Adjustable effort').
- Price (chat model, USD per million tokens): input 0.17, output 0.25 (official-page)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (unspecified)
- Verification: **verified**
- Documented deviations:
  - Model IDs are namespaced <provider>/<model_name>; OpenAI model strings such as gpt-4o or text-embedding-3-large return a 404. — probes: `model-identity.model-identity`; expect: adjustment-needed (official-page)
  - Assistants, Threads and Runs are not implemented. — probes: no probe; expect: unsupported (official-page)
  - OpenAI-shaped fine_tuning.jobs, batches and moderations are not supported; files is only partial (Together has its own Files and Batch APIs). — probes: no probe; expect: unsupported (official-page)
  - logprobs returns Together's own shape, richer than OpenAI's. — probes: `ranges.logprobs`; expect: adjustment-needed (official-page)
  - seed is best-effort; determinism is not guaranteed across replicas, model versions or load. — probes: `ranges.seed`; expect: adjustment-needed (official-page)
  - n is supported on most chat models but not every model; loop client-side if a model rejects it. — probes: `ranges.n-2`; expect: adjustment-needed (official-page)
  - logit_bias is not supported on most models. — probes: no probe; expect: unsupported (official-page)
  - service_tier, store, metadata and prediction are accepted but ignored. — probes: no probe; expect: adjustment-needed; silent (official-page)
  - reasoning_effort works on GPT-OSS models (low, medium, high); Together's reasoning={"enabled": ...} toggle and chat_template_kwargs are not part of OpenAI's API. — probes: `reasoning.reasoning-effort`; expect: compliant (official-page)
  - Vision models accept image_url with remote URLs and base64 data URIs; the detail field is accepted but ignored. — probes: `images.image-part`; expect: compliant; silent (official-page)
  - usage always has prompt_tokens, completion_tokens, total_tokens; cached and reasoning token counts vary in location by model: reasoning models nest them OpenAI-style (prompt_tokens_details.cached_tokens), some non-reasoning models return cached_tokens flat at the top level of usage. A client reading only one shape gets 0 with no error. — probes: `cached-tokens.cached-tokens`; expect: adjustment-needed (official-page)
  - Reasoning models return the chain of thought in a 'reasoning' or 'reasoning_content' field on the assistant message (field name depends on the model), not OpenAI's nested reasoning object. — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - For multi-turn tool calling or preserved thinking, the reasoning must be passed back under the same key (reasoning or reasoning_content) on the prior assistant turn. — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - id and system_fingerprint are present but use Together's formats and should not be parsed as OpenAI ids. — probes: no probe; expect: adjustment-needed (official-page)
  - Errors are OpenAI-shaped {"error": {"message", "type", "code"}} but type and code values are Together's; match on HTTP status (400, 401, 404, 429, 500, 503). — probes: `errors.error-shape`; expect: compliant (official-page)
  - GPT-OSS reasoning guidance says to use the developer role message for system instructions. — probes: `roles.developer-role`; expect: compliant (official-page)
  - Serverless rate limiting returns 429 or 503 under high demand; the rate-limits page documents no rate-limit response headers. — probes: `errors.rate-limit-headers`; expect: unsupported (official-page)
- Docs read:
  - https://docs.together.ai/docs/inference/openai-compatibility — fetched, 2026-10-20
  - https://docs.together.ai/docs/serverless/models — fetched, 2026-10-20
  - https://docs.together.ai/docs/inference/chat/reasoning — fetched, 2026-10-20
  - https://docs.together.ai/docs/serverless/rate-limits — fetched, 2026-10-20
  - https://docs.together.ai/docs/inference/chat/parameters — fetched, 2026-10-20
  - https://docs.together.ai/docs/inference/chat/prompt-caching — fetched, 2026-10-20
  - https://docs.together.ai/docs/inference/chat/structured-outputs — fetched, 2026-10-20
- Notes: Read via curl through the agent proxy (WebFetch DNS fails). The compatibility page's endpoint matrix does not list /v1/responses or any Anthropic endpoint, so responses is null rather than false. Base URL api.together.ai/v1 confirmed (round-1 value correct). Prompt caching is automatic on serverless. Round-1 price/model claims replaced with values from the current serverless table. The last deviation (no rate-limit headers) is an absence on the rate-limits page, not an explicit statement.

### xAI Grok (`xai`)

- Tier / hosting: Priority 1 / hosted
- Secret: `XAI_API_KEY`
- Base URL: `https://api.x.ai/v1` (official-page)
- Auth: bearer via `Authorization` — Examples send 'Authorization: Bearer $XAI_API_KEY'; the OpenAI client takes the key as api_key.
- Models: chat `grok-4.3`, reasoning `grok-4.3` (official-page) — Pricing table rows: grok-build-0.1 $1/$2 (coding model, early access), grok-4.3 $1.25/$2.50 (1M context), grok-4.7/4.6/4.5 $2/$6. grok-4.3 is the cheapest general model: efforts none, low, medium, high, xhigh, default low. Use reasoning_effort none for plain chat. grok-4.7/4.6/4.5 cannot disable reasoning (default high); xhigh only on 4.6 and later (treated as high on grok-4.5). Retired slugs (grok-4-fast-*, grok-3, etc.) redirect to grok-4.3.
- Price (chat model, USD per million tokens): input 1.25, output 2.5 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (unspecified)
- Verification: **partial**
- Documented deviations:
  - Chat Completions is documented as a legacy endpoint; new features arrive on the Responses API first (primary examples use /v1/responses). — probes: `responses.responses-basic`; expect: compliant (official-page)
  - presence_penalty, frequency_penalty and stop cannot be used with reasoning models; requests that include them return an error. — probes: `ranges.stop-5`; expect: unsupported (official-page)
  - reasoning_effort is supported on grok-4.7, 4.6 and 4.5 (default high); reasoning cannot be disabled on those models, while grok-4.3 accepts none (default low). — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - reasoning_effort xhigh is available on grok-4.6 and later; on models that do not support it (grok-4.5) it is treated as high. — probes: `reasoning.reasoning-effort`; expect: adjustment-needed; silent (official-page)
  - Reasoning is exposed through usage reasoning_tokens; on the Responses API grok-4.7 always returns reasoning.encrypted_content and a reasoning summary; no plaintext reasoning_content field is documented on Chat Completions. — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - Encrypted reasoning items must be passed back unchanged in the next request's input to keep reasoning across turns (Responses API). — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - logprobs and top_logprobs are not supported by grok-4.20 and newer models and are silently ignored if set. — probes: `ranges.logprobs`; expect: unsupported; silent (official-page)
  - No role order limitation: system, user and assistant roles can be mixed in any sequence. — probes: `roles.two-system`, `roles.consecutive-user`; expect: compliant (official-page)
  - Message content may be a list of {type:text} parts for system, user and assistant roles; image input uses image_url parts (jpg/jpeg or png, max 20MiB, no image-count limit, any text/image order). — probes: `content-shape.content-parts`, `images.image-part`; expect: compliant (official-page)
  - The xAI API is stateless: prior turns must be resent with each Chat Completions request. — probes: `responses.responses-store`; expect: adjustment-needed (official-page)
  - response_format json_schema (plus json_object and text) is supported; tool-call arguments always conform to the tool schema (strict implicitly true). Subset of JSON Schema: oneOf behaves as anyOf, allOf single subschema only, $ref non-circular only, additionalProperties defaults to false, minLength/maxLength guaranteed up to 2,048, minItems/maxItems 256, minProperties/maxProperties 64. — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: adjustment-needed (official-page)
  - Structured schemas returning 400: enum or anyOf with zero variants, boolean property schemas, maxContains/minContains, items as an array; regex pattern excludes backreferences, \p{..}, \b, lookaround, inline modifiers. — probes: `structured-output.json-schema-strict`; expect: unsupported (official-page)
  - usage.prompt_tokens_details carries text_tokens, audio_tokens, image_tokens and cached_tokens; every response also carries usage.cost_in_usd_ticks. — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Rate limits are per-model requests per second and tokens per minute by spend tier (grok-4.3 Tier 0: 37 RPS, 10M TPM); exceeding any returns 429; no rate-limit response headers are documented on the pages read. — probes: `errors.rate-limit-headers`; expect: unsupported (official-page)
  - Prompts above the 200k-token long-context threshold bill the long-context rate for the whole request. — probes: no probe; expect: adjustment-needed (official-page)
  - Requests to retired slugs (grok-4-fast-*, grok-4-1-fast-*, grok-4-0709, grok-3, grok-code-fast-1) are redirected to grok-4.3 (low effort for reasoning slugs, none for non-reasoning slugs) or grok-build-0.1. — probes: `model-identity.model-identity`; expect: adjustment-needed (official-page)
- Docs read:
  - https://docs.x.ai/overview — fetched, 2026-10-20
  - https://docs.x.ai/developers/models — fetched, 2026-10-20
  - https://docs.x.ai/developers/pricing — fetched, 2026-10-20
  - https://docs.x.ai/developers/model-capabilities/legacy/chat-completions — fetched, 2026-10-20
  - https://docs.x.ai/developers/model-capabilities/text/reasoning — fetched, 2026-10-20
  - https://docs.x.ai/developers/model-capabilities/text/structured-outputs — fetched, 2026-10-20
  - https://docs.x.ai/developers/rate-limits — fetched, 2026-10-20
  - https://docs.x.ai/developers/migration/may-15-retirement — fetched, 2026-10-20
  - https://docs.x.ai/developers/models/grok-4.7 — fetched, 2026-10-20
  - https://docs.x.ai/developers/models/grok-4.3 — fetched, 2026-10-20
  - https://docs.x.ai/developers/release-notes — fetched, 2026-10-20
  - https://docs.x.ai/developers/rest-api-reference/inference — not-reached, 2026-10-20
- Notes: Read via curl through the agent proxy (WebFetch DNS fails). The REST API reference page is client-rendered and returned no parameter list, and the legacy Chat Completions page has no parameter table, so there is no official parameter-by-parameter OpenAI compatibility list (hence partial). The docs site is branded 'SpaceXSI Docs' and the overview heading reads 'Get started with SpaceX Super Intelligence'; the API host and model ids are unchanged. Round-1 claim that the Responses API reference marks presence_penalty unsupported could not be re-verified and was dropped. No /v1/models listing or Anthropic endpoint found in pages read. docs.x.ai/docs/overview redirects to docs.x.ai/overview.

### Z.ai (Zhipu GLM) (`zai`)

- Tier / hosting: Priority 1 / hosted
- Secret: `ZAI_API_KEY`
- Base URL: `https://api.z.ai/api/paas/v4` (official-page)
- Auth: bearer via `Authorization` — Bearer API key. Use a Standard API Key for pay-as-you-go (keys are managed separately from GLM Coding Plan keys since Oct 16, 2026). Coding Plan endpoint https://api.z.ai/api/coding/paas/v4 bills plan quota; using the standard base URL with a Compatible key can deduct account balance.
- Models: chat `glm-5.3-flash`, reasoning `glm-5.3-flash` (official-page) — Pricing page 'Latest Models': GLM-5.3-Flash $0.15 in / $0.50 out, GLM-5.3-FlashX $0.37/$1.25, GLM-5.3 and GLM-5.2 $1.4/$4.4; older GLM-4.7 $0.6/$2.2. Free (legacy, under 'Other Pricing'): GLM-4.7-Flash, GLM-4.5-Flash. glm-5.3-flash appears in the Vision request model enum (multimodal) with thinking forced on and reasoning_effort low/high/max (default max); the Text request enum lists glm-5.3, 5.2, 5.1, 5, 4.7, 4.6. Documented parameter sets differ by schema: the vision schema (flash) lists do_sample, thinking, reasoning_effort, temperature, top_p, max_tokens, tools, tool_choice, stop, request_id, user_id (no response_format or tool_stream); the text schema adds response_format, tool_stream and a stream/max_tokens block. Use glm-4.7 or glm-5.3 when probing response_format or tool_stream.
- Price (chat model, USD per million tokens): input 0.15, output 0.5 (official-page: docs.z.ai pricing page, GLM-5.3-Flash (cached input $0.03); cached storage limited-time free)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True, `https://api.z.ai/api/anthropic` (official-page)
- Verification: **verified**
- Documented deviations:
  - temperature must be within [0.0, 1.0] (default 1.0); top_p within [0.01, 1.0] (default 0.95); recommend tuning only one — probes: `ranges.temperature-2`; expect: unsupported (official-page)
  - Output cap is max_tokens only (1 to 131072, 128K max output); max_completion_tokens is not in the request schema — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - stop: 'Currently, only one stop word is supported' (array form ["stop_word1"]; schema also says maxItems 4) — probes: `ranges.stop-5`; expect: unsupported (official-page)
  - tool_choice: only 'auto' is supported (default auto); at most 128 functions per request; tools may also be retrieval or web_search types — probes: `tools.tool-required`; expect: unsupported (official-page)
  - response_format only text and json_object ('Only text models support this field'); no json_schema type — probes: `structured-output.json-schema-strict`; expect: unsupported (official-page)
  - response_format {type: json_object} is supported on text models (not in the vision/flash request schema) — probes: `structured-output.json-object`; expect: compliant (official-page)
  - Message roles are system, user, assistant, tool (no developer); 'The input must not consist of system messages or assistant messages only'; no name field in the message schemas — probes: `roles.developer-role`, `roles.name-field`; expect: unsupported (official-page)
  - Text models: user content is a string only; multimodal arrays (text, image_url, video_url, file) are for vision-capable models such as glm-5.3-flash/flashx, glm-4.6v — probes: `content-shape.content-parts`, `images.image-part`; expect: adjustment-needed (official-page)
  - Streaming: usage and finish_reason appear only in the last chunk; stream_options is not in the request schema; delta carries reasoning_content and content — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
  - Streaming tool-call arguments require non-standard tool_stream=true together with stream=true (GLM-5.3/5.2/5.1/5/4.7/4.6 series); arguments arrive in fragments — probes: `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
  - usage.prompt_tokens_details.cached_tokens reports implicit context-cache hits — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Non-standard thinking {type: enabled|disabled, clear_thinking}; thinking is forced on for GLM-5.3 and GLM-5.3-Flash (disabling errors); reasoning returned in reasoning_content (delta and message) — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort supported by GLM-5.2 and above, default max: GLM-5.3/5.3-Flash only low/high/max; GLM-5.2 maps none/minimal to skip thinking, low/medium to high, xhigh to max — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - clear_thinking defaults to true (prior-turn reasoning_content is ignored); Preserved Thinking needs clear_thinking=false and the full, unmodified, correctly ordered historical reasoning_content — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Errors: {error: {code: "1214", message}} with string business codes (1000/1001 auth 401, 1113 balance 429, 1210-1214 invalid params 400, 1302/1305 rate limit 429); there is no type or param field; abnormal SSE termination is reported via finish_reason (sensitive, model_context_window_exceeded, network_error) instead of an error code; rate-limit headers not documented — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
  - request_id (6-64 chars) and user_id (6-128 chars) are Z.ai request fields; do_sample appears in the vision/flash schema — probes: no probe; expect: adjustment-needed (official-page)
- Docs read:
  - https://docs.z.ai/api-reference/llm/chat-completion.md — fetched, 2026-10-20
  - https://docs.z.ai/guides/develop/openai/python.md — fetched, 2026-10-20
  - https://docs.z.ai/guides/overview/pricing.md — fetched, 2026-10-20
  - https://docs.z.ai/guides/overview/migrate-to-glm-new.md — fetched, 2026-10-20
  - https://docs.z.ai/guides/capabilities/streaming.md — fetched, 2026-10-20
  - https://docs.z.ai/guides/capabilities/struct-output.md — fetched, 2026-10-20
  - https://docs.z.ai/guides/capabilities/cache.md — fetched, 2026-10-20
  - https://docs.z.ai/api-reference/api-code.md — fetched, 2026-10-20
  - https://docs.z.ai/devpack/notice/api-key-management.md — fetched, 2026-10-20
  - https://docs.z.ai/scenario-example/develop-tools/claude — fetched, 2026-10-20
  - https://docs.z.ai/api-reference/rate-limit.md — not-reached, 2026-10-20
- Notes: Read via curl through the sandbox proxy (WebFetch fails DNS). The rate-limit page failed (proxy CONNECT 403 on every retry) so no RPM figures. n, seed, logprobs, stream_options, parallel_tool_calls, penalties are absent from the request schemas (absence only, not documented as unsupported). No Responses or models-list endpoint appears in llms.txt. China-region base (open.bigmodel.cn) not re-checked and dropped. Round-1 glm-4.7 third-party prices replaced by official pricing page.

## Priority 1 · gateway

### OpenRouter (`openrouter`)

- Tier / hosting: Priority 1 / gateway
- Secret: `OPENROUTER_API_KEY`
- Base URL: `https://openrouter.ai/api/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <OPENROUTER_API_KEY>. Optional attribution headers HTTP-Referer, X-OpenRouter-Title (X-Title also accepted), X-OpenRouter-Categories.
- Models: chat `nvidia/nemotron-3-super-120b-a12b:free`, reasoning `nvidia/nemotron-3-super-120b-a12b:free` (official-page) — chat and reasoning use one pinned :free slug because openrouter/free picks a different free model per request (seen in a live run); openrouter/free (Free Models Router) is documented and appears in GET /api/v1/models with $0 pricing, 200K context, supported_parameters including reasoning/tools/structured_outputs; it picks a random free model per request, so pin a :free slug for repeatable probes. nvidia/nemotron-3-super-120b-a12b:free is in the live models list with $0 pricing and supported_parameters reasoning, tools, structured_outputs (checked 2026-10-20); the free roster churns frequently.
- Price (chat model, USD per million tokens): input 0, output 0 (official-page)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://openrouter.ai/api/v1/messages` (official-page)
- Verification: **verified**
- Documented deviations:
  - If the chosen model does not support a request parameter (e.g. logit_bias on non-OpenAI models, top_k for OpenAI), the parameter is ignored; the rest are forwarded to the underlying model API. — probes: `ranges.logprobs`, `ranges.seed`; expect: adjustment-needed; silent (official-page)
  - Streaming: on Chat Completions every stream ends with an extra usage chunk just before [DONE]; unlike OpenAI's spec it carries a non-empty choices array (one content-free delta repeating finish_reason and native_finish_reason), so finish_reason appears twice. Usage is returned without requesting stream_options. Usage also carries cost, cost_details, is_byok. — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
  - SSE streams occasionally contain comment lines (': OPENROUTER PROCESSING') that parsers must skip; an error after streaming starts arrives as an SSE data event with an error field and the HTTP status stays 200. — probes: no probe; expect: adjustment-needed (official-page)
  - Reasoning is configured via a `reasoning` object (effort, summary, enabled/max_tokens/exclude) and the shorthand top-level reasoning_effort (max, xhigh, high, medium, low, minimal, none); they cannot differ if both are sent. Assistant messages carry `reasoning` (string) and `reasoning_details`, not reasoning_content. — probes: `reasoning.reasoning-effort`, `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - max_tokens and max_completion_tokens share semantics; for reasoning models the limit covers reasoning plus visible output on most providers, so a small value can return 200 with finish_reason length and empty content. — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - Responses API (/api/v1/responses) is stateless: requests that set store: true or a non-null previous_response_id are rejected with a 400 error. — probes: `responses.responses-basic`, `responses.responses-store`; expect: unsupported (official-page)
  - Error body is {error:{code:number,message,metadata?}} with HTTP status equal to error.code; documented codes 400, 401, 402, 403, 408, 429, 502, 503; Retry-After may accompany 429/503/402; the Responses API error body uses string codes (e.g. invalid_prompt) and a top-level metadata field. — probes: `errors.error-shape`; expect: adjustment-needed (official-page)
  - Successful inference responses do not include X-RateLimit-* headers; only OpenRouter-originated platform-limit errors carry X-RateLimit-Limit/Remaining/Reset (and Retry-After when every attempted provider returned a hint); remaining quota via GET /api/v1/key. — probes: `errors.rate-limit-headers`; expect: unsupported (official-page)
  - OpenRouter selects the least expensive/best provider for the model and falls back to other providers on 5xx or rate limits; responses are normalized to the OpenAI Chat schema and carry extra fields (native_finish_reason, cost, reasoning_details, models/route/provider/plugins request fields). — probes: `model-identity.model-identity`; expect: adjustment-needed (official-page)
  - If message `name` is included it is prepended as '{name}: {content}' for non-OpenAI models; content parts are only for the user role; image_url accepts a URL or base64 data with optional detail. — probes: `roles.name-field`, `images.image-part`, `content-shape.content-parts`; expect: adjustment-needed (official-page)
  - Message schema includes system, user, assistant, tool and developer roles; a trailing assistant message is treated as a prefill. — probes: `roles.developer-role`, `roles.consecutive-user`; expect: compliant (official-page)
  - response_format json_object and json_schema are supported but depend on the routed model (check models page supported_parameters=structured_outputs). — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: adjustment-needed (official-page)
  - tool_choice none/auto/required/function is documented; tools are mapped or templated for non-OpenAI providers, so support is per model. — probes: `tools.tool-required`; expect: adjustment-needed (official-page)
  - stop accepts up to 4 sequences (schema maxItems 4); temperature range [0,2]. — probes: `ranges.stop-5`, `ranges.temperature-2`; expect: adjustment-needed (official-page)
  - Free model limits (ids ending :free): 20 requests/min and 50/day with less than $10 credits purchased all-time; 20/min and 1000/day with at least $10. — probes: no probe; expect: adjustment-needed (official-page)
- Docs read:
  - https://openrouter.ai/docs/api_reference/overview.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api_reference/parameters.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api_reference/streaming.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api_reference/errors-and-debugging.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api_reference/limits.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api_reference/responses/overview.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api_reference/authentication.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/api/api-reference/anthropic-messages/create-a-message.md — fetched, 2026-10-20
  - https://openrouter.ai/docs/guides/routing/routers/free-router.md — fetched, 2026-10-20
  - https://openrouter.ai/api/v1/models — fetched, 2026-10-20
- Notes: Official overview, parameters, streaming, errors, limits, Responses and chat-completion OpenAPI pages all read. Round 1 corrections: top-level reasoning_effort IS documented (shorthand for reasoning.effort); Anthropic Messages endpoint confirmed (OpenAPI path /messages on server https://openrouter.ai/api/v1); successful responses carry no X-RateLimit-* headers. CI-friendly with a free key but behavior varies per routed model; pin a :free slug. openrouter.ai fails WebFetch DNS but works via curl.

## Priority 1 · local

### llama.cpp server (`llamacpp`)

- Tier / hosting: Priority 1 / local
- Secret: none (local host)
- Base URL: `http://127.0.0.1:8080/v1` (official-page)
- Auth: bearer via `Authorization` — No auth by default. --api-key (comma-separated list), --api-key-file or LLAMA_API_KEY env enables Bearer auth; /health stays public. NOTE: the README's current default port is 9931 and default host 127.0.0.1; 8080 is the port used in the README's docker examples with explicit --port 8080 --host 0.0.0.0.
- Models: chat `qwen3-1.7b`, reasoning `qwen3-1.7b` (workflow: the model local_host.sh starts) — one small model serves both roles
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `/v1/messages and /v1/messages/count_tokens (SSE streaming; tool use requires --jinja)` (official-page)
- Verification: **partial**
- Local host: image `ghcr.io/ggml-org/llama.cpp:server (CPU; docs/docker.md lists linux/amd64, arm64, s390x; manifest checked on GHCR: amd64 and arm64 present). CUDA variant is :server-cuda`; CPU image available: True; reasoning fits a CI runner: True (official-page (README, docker.md, GHCR manifest, HF API). Fit on 4 vCPU/16GB is an estimate (Q4_K_M 1.7B roughly 1.1GB, repo total 4.07GB includes unused quants); not run here.)
- Documented deviations:
  - README: 'While no strong claims of compatibility with OpenAI API spec is being made, in our experience it suffices to support many apps' (chat and completions); only models with a supported chat template work optimally, ChatML is the default — probes: no probe; expect: adjustment-needed (official-page)
  - OpenAI-style function calling is supported with --jinja (now documented as enabled by default) and may need a --chat-template-file override for a tool-capable template (worst case --chat-template chatml) — probes: `tools.tool-required`, `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
  - response_format accepts json_object and json_schema, plus an extension: {type: json_object, schema: {...}} for schema-constrained JSON — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (official-page)
  - Thoughts are returned according to --reasoning-format (none leaves them in content; deepseek moves them to message.reasoning_content; deepseek-legacy does both); per-request reasoning_format overrides parsing — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort: 'none' disables thinking, any other value is passed to the jinja template; reasoning_control arms real-time control via /v1/chat/completions/control — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - /v1/responses is OpenAI-compatible (README defers to the OpenAI reference; no parameter list); also /v1/responses/input_tokens and /v1/chat/completions/input_tokens — probes: `responses.responses-basic`, `responses.responses-store`; expect: adjustment-needed (official-page)
  - Response usage includes prompt_tokens_details.cached_tokens — probes: `cached-tokens.cached-tokens`; expect: compliant (official-page)
  - Errors use {error: {code, message, type}} (e.g. 400 invalid_request_error, 503 unavailable_error while the model loads) — probes: `errors.error-shape`; expect: compliant (official-page)
  - llama.cpp-specific options such as mirostat are accepted on the OpenAI-style routes; chat_template_kwargs passes template args — probes: no probe; expect: compliant (official-page)
  - input_audio.format is ignored (file format auto-detected); multimodal needs an mmproj (auto-downloaded with -hf when available) — probes: `images.image-part`; expect: adjustment-needed; silent (official-page)
- Docs read:
  - https://raw.githubusercontent.com/ggml-org/llama.cpp/master/tools/server/README.md — fetched, 2026-10-20
  - https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/docker.md — fetched, 2026-10-20
  - https://ghcr.io/v2/ggml-org/llama.cpp/manifests/server — fetched, 2026-10-20
  - https://huggingface.co/api/models/ggml-org/Qwen3-1.7B-GGUF — fetched, 2026-10-20
  - https://huggingface.co/api/models/Qwen/Qwen3-0.6B-GGUF — fetched, 2026-10-20
  - https://huggingface.co/api/models/ggml-org/Qwen3-0.6B-GGUF — fetched, 2026-10-20
- Notes: README read in full via GitHub raw (~120k chars, master as of 2026-10-20). It does not enumerate supported/unsupported OpenAI parameters (it defers to the OpenAI reference), so per-parameter probe expectations are unknown; hence partial. Best CI fit after Ollama. Poll GET /health (503 'Loading model' until ready). Parallel probes may queue: -np/--parallel default is auto (-1).

### Ollama (`ollama`)

- Tier / hosting: Priority 1 / local
- Secret: none (local host)
- Base URL: `http://127.0.0.1:11434/v1` (official-page)
- Auth: bearer via `Authorization` — Local server: SDKs need an api_key value (e.g. 'ollama') but Ollama ignores it. Direct cloud API https://ollama.com/v1 requires OLLAMA_API_KEY; the cloud API does not support stateful Responses, built-in web search through /v1/responses, or custom/freeform tool-call replay.
- Models: chat `hf.co/ggml-org/Qwen3-1.7B-GGUF:Q4_K_M`, reasoning `hf.co/ggml-org/Qwen3-1.7B-GGUF:Q4_K_M` (workflow: the model local_host.sh starts) — one small model serves both roles
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (not covered by the OpenAI compatibility page)
- Verification: **verified**
- Local host: image `ollama/ollama (Docker Hub; docs 'CPU only' command, no GPU flags)`; CPU image available: True; reasoning fits a CI runner: True (official-page for docker run, /api/pull shape, base URL and /v1 behavior; Hugging Face docs for hf.co syntax; HF API for repo files. Fit on 4 vCPU/16GB is an estimate (Q4_K_M 1.7B roughly 1.1GB); not run here.)
- Documented deviations:
  - /v1/chat/completions: tool_choice, logit_bias, user and n are not supported (unchecked in the supported-fields list) — probes: `tools.tool-required`, `ranges.n-2`; expect: unsupported (official-page)
  - Logprobs are not supported on chat completions or completions — probes: `ranges.logprobs`; expect: unsupported (official-page)
  - Images: base64-encoded only; image URLs are not supported; array-of-content-parts messages are supported — probes: `images.image-part`, `content-shape.content-parts`; expect: adjustment-needed (official-page)
  - Supported chat fields: model, messages, frequency_penalty, presence_penalty, response_format (JSON mode), seed, stop, stream, stream_options.include_usage, temperature, top_p, max_tokens, tools, reasoning_effort, reasoning.effort; max_completion_tokens is not listed — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`, `ranges.seed`, `ranges.stop-5`, `streaming-usage.usage-included`, `structured-output.json-object`; expect: adjustment-needed (official-page)
  - reasoning_effort / reasoning.effort: use /api/show for each model's supported values; with metadata unsupported names resolve to the model default; boolean-only models map efforts to true and 'none' to false; without metadata minimal->low and xhigh/ultra->max; GPT-OSS maps minimal->low, xhigh/ultra->high, none->no thinking — probes: `reasoning.reasoning-effort`, `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - /v1/responses (Ollama v0.13.3+) is non-stateful only: supports model, input, instructions, tools, stream, temperature, top_p, max_output_tokens, reasoning.effort, think; previous_response_id, conversation and truncation are not supported — probes: `responses.responses-basic`; expect: compliant (official-page)
  - Stateful Responses requests (previous_response_id, conversation) are not supported — probes: `responses.responses-store`; expect: unsupported (official-page)
  - Context size cannot be set through the OpenAI API; create a Modelfile with PARAMETER num_ctx and `ollama create`, then use the new model name — probes: no probe; expect: adjustment-needed (official-page)
  - /v1/models: created is last-modified time, owned_by is the ollama username (default 'library'); /v1/completions prompt accepts only a string — probes: no probe; expect: adjustment-needed (official-page)
- Docs read:
  - https://raw.githubusercontent.com/ollama/ollama/main/docs/api/openai-compatibility.mdx — fetched, 2026-10-20
  - https://raw.githubusercontent.com/ollama/ollama/main/docs/docker.mdx — fetched, 2026-10-20
  - https://docs.ollama.com/api/pull — fetched, 2026-10-20
  - https://huggingface.co/docs/hub/ollama — fetched, 2026-10-20
  - https://huggingface.co/api/models/ggml-org/Qwen3-1.7B-GGUF — fetched, 2026-10-20
  - https://ollama.com/library/qwen3/tags — not-reached, 2026-10-20
- Notes: Easiest local host for CI: single small image, CPU by default. Read via curl through the sandbox proxy (WebFetch fails DNS). The compat page lists no JSON-schema (response_format json_schema) detail and no error/rate-limit shape. Round-1 'Responses unsupported' was wrong: basic Responses is supported, only the stateful flavor is not.

### SGLang (`sglang`)

- Tier / hosting: Priority 1 / local
- Secret: none (local host)
- Base URL: `http://127.0.0.1:30000/v1` (official-page)
- Auth: bearer via `Authorization` — No auth by default (docs examples use api_key='None'). Port 30000 is used by the docs' launch commands (--port 30000); --api-key flag exists in server args (source) but was not read in docs.
- Models: chat `Qwen/Qwen3-0.6B`, reasoning `Qwen/Qwen3-0.6B` (workflow: the model local_host.sh starts) — one small model serves both roles
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `http://localhost:30000/v1/messages (base_url http://127.0.0.1:30000 for the Anthropic SDK); route on by default for any model; SGLang does not validate the request model field` (official-page)
- Verification: **partial**
- Local host: image `lmsysorg/sglang:v0.5.22-xeon (docs: 'The CPU image tags end with xeon suffix', example tag v0.5.13-xeon; Docker Hub shows v0.5.22-xeon pushed 2026-10-15, linux/amd64 only, ~2.0GB compressed). Alternatively build docker/xeon.Dockerfile`; CPU image available: True; reasoning fits a CI runner: None (official-page (docs cpu_server.mdx, xeon.Dockerfile, release workflow, Docker Hub API) for image, launch flags and tag existence; reasoning parser from reasoning docs. Not run; fit depends on whether the runner CPU supports the AMX-optimized kernels and on a 2GB image pull.)
- Documented deviations:
  - Docs: 'The server fully implements the OpenAI API'; chat template taken from the HF tokenizer, override with --chat-template — probes: no probe; expect: compliant (official-page)
  - Reasoning needs a model-matched --reasoning-parser at launch (qwen3 for standard Qwen3) and chat_template_kwargs (enable_thinking) per request; separate_reasoning (extra_body) toggles splitting of reasoning from content, stream_reasoning controls streaming — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - ChatCompletionRequest in server source accepts reasoning_effort, max_completion_tokens, n, seed, logprobs, stop, stream_options, tool_choice, parallel_tool_calls (default true), response_format, user plus SGLang extras (top_k, min_p, regex, ebnf, stop_token_ids, ...); this is source code, not a docs page — probes: `reasoning.reasoning-effort`, `output-cap.max-completion-tokens`, `ranges.n-2`, `ranges.seed`, `ranges.logprobs`; expect: compliant (official-page)
  - /v1/responses, /v1/responses/{response_id} and /v1/responses/{response_id}/cancel routes are defined in python/sglang/srt/entrypoints/http_server.py (source, not docs); stateful behavior was not documented — probes: `responses.responses-basic`, `responses.responses-store`; expect: compliant (official-page)
  - Tool calling needs a --tool-call-parser matching the model (tool_parser docs page, not read in detail) — probes: `tools.tool-required`, `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
- Docs read:
  - https://raw.githubusercontent.com/sgl-project/sglang/main/docs/docs/hardware-platforms/cpu_server.mdx — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/docs/docs/basic_usage/openai_api_completions.mdx — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/docs/docs/advanced_features/separate_reasoning.mdx — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/docs/docs/basic_usage/anthropic_api.mdx — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/docs/docs/get-started/install.mdx — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/docker/xeon.Dockerfile — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/.github/workflows/release-docker-xeon.yml — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/python/sglang/srt/entrypoints/openai/protocol.py — fetched, 2026-10-20
  - https://raw.githubusercontent.com/sgl-project/sglang/main/python/sglang/srt/entrypoints/http_server.py — fetched, 2026-10-20
  - https://hub.docker.com/v2/repositories/lmsysorg/sglang/tags — fetched, 2026-10-20
  - https://docs.sglang.io/docs/hardware-platforms/cpu_server — not-reached, 2026-10-20
- Notes: docs.sglang.io/docs.sglang.ai are blocked by the sandbox proxy; read the docs' mdx sources on GitHub raw instead (docs/docs/*.mdx). The docs state no per-parameter deviation list; request fields come from protocol.py source. CORRECTION to round 1: a prebuilt CPU image does exist (lmsysorg/sglang:*-xeon, docs + Docker Hub). Caveat: SGLang CPU path is documented as enabled and optimized for Intel Xeon with AMX (4th gen or newer); behavior on runners without AMX is not documented.

### vLLM (`vllm`)

- Tier / hosting: Priority 1 / local
- Secret: none (local host)
- Base URL: `http://127.0.0.1:8000/v1` (official-page)
- Auth: bearer via `Authorization` — No auth by default; --api-key (or VLLM_API_KEY) enables Bearer auth, but only for /v1, /v2 and /inference path prefixes; other endpoints such as /invocations stay unauthenticated.
- Models: chat `Qwen/Qwen3-0.6B`, reasoning `Qwen/Qwen3-0.6B` (workflow: the model local_host.sh starts) — one small model serves both roles
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (not mentioned on the OpenAI-compatible server page)
- Verification: **verified**
- Local host: image `vllm/vllm-openai-cpu:latest-x86_64 (docs). Pin in CI: vllm/vllm-openai-cpu:v0.32.0-x86_64 (Docker Hub tag pushed 2026-10-18, amd64 ~1.7GB compressed); multi-arch tags latest and v0.32.0 also exist`; CPU image available: True; reasoning fits a CI runner: True (official-page: install CPU docs (image names, docker run shape, launch example), Dockerfile.cpu (entrypoint), reasoning_outputs.md (Qwen3-0.6B + qwen3 parser), Docker Hub API (tags). Fit on 4 vCPU/16GB is an estimate (0.6B bf16 weights ~1.2GB plus 4GiB KV cache); not run here.)
- Documented deviations:
  - Chat Completions: the user parameter is ignored — probes: no probe; expect: unsupported; silent (official-page)
  - Chat Completions: image_url.detail is not supported — probes: `images.image-part`; expect: adjustment-needed (official-page)
  - parallel_tool_calls=false returns zero or one tool call; true (default) allows several but the model may still return one — probes: `tools.tool-required`; expect: compliant (official-page)
  - Chat is only available for text-generation models that have a chat template; Completions suffix parameter is not supported — probes: no probe; expect: adjustment-needed (official-page)
  - By default the server applies the model repo's generation_config.json, overriding sampling defaults; disable with --generation-config vllm — probes: `ranges.temperature-2`, `ranges.seed`; expect: adjustment-needed (official-page)
  - Non-OpenAI parameters (top_k, structured_outputs, ...) are passed as extra parameters (extra_body or merged into the JSON payload) — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (official-page)
  - Reasoning output requires starting the server with --reasoning-parser <name> (qwen3 for Qwen3); reasoning goes to reasoning_content; model-specific enable_thinking via chat_template_kwargs — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - reasoning_effort (chat) / reasoning.effort (responses): low, medium, high inject enable_thinking=true, none injects false, unset injects nothing — probes: `reasoning.reasoning-effort`; expect: compliant (official-page)
  - Responses API: /v1/responses, /v1/responses/{response_id}, /v1/responses/{response_id}/cancel; text-generation models only; vLLM sampling params (top_k, min_p, repetition_penalty, seed, stop, ignore_eos) are forwarded — probes: `responses.responses-basic`, `responses.responses-store`; expect: compliant (official-page)
  - X-Request-Id header needs --enable-request-id-headers; X-Vllm-Priority header needs priority scheduling for non-zero values — probes: no probe; expect: adjustment-needed (official-page)
- Docs read:
  - https://raw.githubusercontent.com/vllm-project/vllm/main/docs/serving/online_serving/openai_compatible_server.md — fetched, 2026-10-20
  - https://raw.githubusercontent.com/vllm-project/vllm/main/docs/getting_started/installation/cpu.x86.inc.md — fetched, 2026-10-20
  - https://raw.githubusercontent.com/vllm-project/vllm/main/docs/getting_started/installation/cpu.md — fetched, 2026-10-20
  - https://raw.githubusercontent.com/vllm-project/vllm/main/docs/features/reasoning_outputs.md — fetched, 2026-10-20
  - https://raw.githubusercontent.com/vllm-project/vllm/main/docker/Dockerfile.cpu — fetched, 2026-10-20
  - https://raw.githubusercontent.com/vllm-project/vllm/main/vllm/engine/arg_utils.py — fetched, 2026-10-20
  - https://hub.docker.com/v2/repositories/vllm/vllm-openai-cpu/tags — fetched, 2026-10-20
  - https://docs.vllm.ai/en/latest/getting_started/installation/cpu/ — not-reached, 2026-10-20
- Notes: docs.vllm.ai is behind a Cloudflare challenge; used the GitHub raw markdown of the same pages (main branch as of 2026-10-20). Chat-completion sampling-param tables are generated from code snippets not rendered in the markdown, so no complete unsupported-param list. The page does not mention GET /v1/models explicitly (poll it anyway, or POST a tiny chat request). Heaviest local host: ~1.7GB compressed image, slow start. CPU backend supports x86, ARM, Apple, s390x.

## Priority 2 · hosted

### Baseten Model APIs (`baseten`)

- Tier / hosting: Priority 2 / hosted
- Secret: `BASETEN_API_KEY`
- Base URL: `https://inference.baseten.co/v1` (search-summary)
- Auth: bearer via `Authorization` — Baseten API key as the OpenAI SDK api_key; exact header form not read. Native Baseten uses 'Authorization: Api-Key <key>'. Dedicated deployments use https://model-<id>.api.baseten.co/environments/production/sync/v1.
- Models: chat `openai/gpt-oss-120b`, reasoning `openai/gpt-oss-120b` (search-summary) — Reasoning page snippet lists openai/gpt-oss-120b with reasoning on by default and reasoning_effort supported (default medium). Other current slugs per snippets: deepseek-ai/DeepSeek-V4-Pro, deepseek-ai/DeepSeek-V4-Pro-0813, deepseek-ai/DeepSeek-V4-Flash-0731, zai-org/GLM-5.3, zai-org/GLM-5.2, zai-org/GLM-4.7, zai-org/GLM-5.3-Flash, moonshotai/Kimi-K3, moonshotai/Kimi-K2.6, moonshotai/Kimi-K2.7-Code. Round-1 slugs (DeepSeek-V3.1, Kimi-K2-Thinking) were not confirmed current. Cline reports Kimi K2 0711, Llama 4 Maverick and Scout deprecated Oct 8. No plain non-reasoning cheap chat model confirmed, so gpt-oss-120b fills both slots; check the live models list.
- Price (chat model, USD per million tokens): input 0.1, output 0.5 (search-summary (third-party: Cline docs and pricepertoken.com for gpt-oss-120b; Baseten official per-model price list not retrieved; indicative only))
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True (search-summary (Model APIs overview says endpoints are compatible with both OpenAI Chat Completions and Anthropic Messages API; path not read))
- Verification: **unverified**
- Documented deviations:
  - All Model APIs models support tool calling (function calling), structured outputs and JSON mode — probes: `tools.tool-required`, `structured-output.json-schema-strict`, `structured-output.json-object`; expect: compliant (search-summary)
  - Reasoning and vision support vary by model — probes: `reasoning.reasoning-shape`, `images.image-part`; expect: adjustment-needed (search-summary)
  - gpt-oss-120b accepts reasoning_effort (default medium); reasoning enabled by default — probes: `reasoning.reasoning-effort`; expect: compliant (search-summary)
  - Kimi K2.6 and K2.7-Code need chat_template_args to turn reasoning on; Kimi K3 has it on by default — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (search-summary)
- Docs read:
  - https://docs.baseten.co/inference/model-apis/overview — search-summary, 2026-10-20
  - https://docs.baseten.co/inference/model-apis/reasoning — search-summary, 2026-10-20
  - https://docs.baseten.co/reference/inference-api/chat-completions — search-summary, 2026-10-20
  - https://docs.baseten.co/inference/model-apis/overview.md — not-reached, 2026-10-20
  - https://docs.baseten.co/development/model-apis/overview — not-reached, 2026-10-20
- Notes: docs.baseten.co fails DNS in the sandbox. Docs path moved from /development/model-apis to /inference/model-apis. Docs reportedly bill per million tokens with cached input discounted. A Chat Completions reference page exists (reference/inference-api/chat-completions) but its parameter table was not readable. No documented deviation list found.

### Cloudflare Workers AI (`cloudflare`)

- Tier / hosting: Priority 2 / hosted
- Secret: `CLOUDFLARE_API_KEY`
- Base URL: `https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/ai/v1` (search-summary)
- Auth: bearer via `Authorization` — Cloudflare API token as Bearer. Needs account id in URL (second variable, e.g. CLOUDFLARE_ACCOUNT_ID). Base must stop at /ai/v1; client appends /chat/completions or /responses.
- Models: chat `@cf/meta/llama-3.1-8b-instruct-fp8`, reasoning `@cf/openai/gpt-oss-120b` (search-summary) — Models use @cf/ prefix. fp8 Llama 8B model page lists 32,000 ctx at $0.152/$0.287 per M. gpt-oss-120b page lists 128k ctx, function calling, $0.35/$0.75 per M, and says Workers AI supports OpenAI-compatible /v1/chat/completions and /v1/embeddings. Launch changelog (2025-08-05) says /ai/v1/responses accepts Responses format for gpt-oss-120b and gpt-oss-20b and listed chat completions for gpt-oss as 'coming soon'; current compat docs example calls gpt-oss-120b through responses.create. Workers AI changelog says the Workers Free plan now blocks some resource-intensive models (not checked whether gpt-oss-120b is one).
- Price (chat model, USD per million tokens): input 0.152, output 0.287 (search-summary (Workers AI pricing page snippet, llama-3.1-8b-instruct-fp8; $0.011 per 1,000 Neurons, daily free allocation))
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (round-1 claim of an AI Gateway /messages route not re-confirmed; removed)
- Verification: **partial**
- Documented deviations:
  - Responses API (/ai/v1/responses) documented for the GPT-OSS models (gpt-oss-120b, gpt-oss-20b) — probes: `responses.responses-basic`, `responses.responses-store`; expect: adjustment-needed (search-summary)
  - OpenAI-compatible endpoints cover text generation (/v1/chat/completions) and text embeddings only; most, not all, text generation models support Chat Completions — probes: no probe; expect: adjustment-needed (search-summary)
  - Whether gpt-oss models work on /chat/completions is documented inconsistently (model page says yes; launch changelog said coming soon) — probes: no probe; expect: adjustment-needed (search-summary)
- Docs read:
  - https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/ — search-summary, 2026-10-20
  - https://developers.cloudflare.com/workers-ai/platform/pricing/ — search-summary, 2026-10-20
  - https://developers.cloudflare.com/workers-ai/models/gpt-oss-120b/ — search-summary, 2026-10-20
  - https://developers.cloudflare.com/changelog/2025-08-05-openai-open-models — search-summary, 2026-10-20
  - https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/index.md — not-reached, 2026-10-20
- Notes: developers.cloudflare.com and its preview hosts fail DNS; no parameter-level deviation list retrieved. Free daily Neuron allowance makes it CI-feasible but needs account id plus token. @cf/meta/llama-3.1-8b-instruct (non-fp8) shows a deprecation date of 2026-05-30 in round-1 data; fp8 variant status not checked.

### Cohere (Compatibility API) (`cohere`)

- Tier / hosting: Priority 2 / hosted
- Secret: `COHERE_API_KEY`
- Base URL: `https://api.cohere.ai/compatibility/v1` (search-summary)
- Auth: bearer via `Authorization` — Cohere API key as OpenAI SDK api_key
- Models: chat `command-r7b-12-2024`, reasoning `command-a-reasoning-08-2025` (search-summary) — Models-docs snippets: command-r7b-12-2024 $0.0375/$0.15 per M, 128K ctx (cheapest); command-a-03-2025 $2.50/$10 (page ~586 days old); command-a-reasoning-08-2025 live, 256K ctx, free for trial and production keys until rate limits; Command A+ command-a-plus-05-2026 also live. Strands docs name command-r-plus, command-a-03-2025, embed-v4.0 as compat-compatible ids; whether command-r7b works on the compat endpoint was not confirmed. Third-party report (open-source project) says command-a-reasoning accepted only reasoning_effort=low and 422'd on medium/high, which conflicts with the compat page's none/high statement; probe it.
- Price (chat model, USD per million tokens): input 0.0375, output 0.15 (search-summary (docs.cohere.com Command R7B page snippet))
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not checked)
- Verification: **partial**
- Documented deviations:
  - Supported chat params: model, messages, stream, reasoning_effort, response_format, tools, temperature, max_tokens, stop, seed, top_p, frequency_penalty, presence_penalty — probes: `output-cap.max-tokens`, `ranges.seed`, `ranges.stop-5`, `structured-output.json-object`, `streaming-usage.usage-included`; expect: compliant (search-summary)
  - Unsupported chat param: n — probes: `ranges.n-2`; expect: unsupported (search-summary)
  - Unsupported chat param: top_logprobs (logprobs itself is in neither list) — probes: `ranges.logprobs`; expect: unsupported (search-summary)
  - Unsupported chat params: store, metadata, logit_bias, modalities, prediction, audio, service_tier, parallel_tool_calls — probes: no probe; expect: unsupported (search-summary)
  - reasoning_effort: only none and high work; medium and low are not supported at this time — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (search-summary)
  - Parameters unique to the Cohere API and absent from the OpenAI SDK (connectors, documents, citation_options, input_type, images, truncate) are not supported — probes: no probe; expect: unsupported (search-summary)
  - Embeddings: dimensions and user unsupported (round-1 snippet) — probes: no probe; expect: unsupported (search-summary)
  - max_completion_tokens, tool_choice, logprobs and stream_options are in neither the supported nor unsupported list — probes: `output-cap.max-completion-tokens`, `tools.tool-required`, `ranges.logprobs`, `streaming-usage.usage-included`; expect: adjustment-needed (search-summary)
- Docs read:
  - https://docs.cohere.com/docs/compatibility-api — search-summary, 2026-10-20
  - https://docs.cohere.com/docs/models — search-summary, 2026-10-20
  - https://docs.cohere.com/docs/command-a-reasoning — search-summary, 2026-10-20
  - https://docs.cohere.com/changelog/compatibility-api — search-summary, 2026-10-20
- Notes: docs.cohere.com fails DNS from WebFetch; compat-page lists come from search snippets quoting the page. Changelog snippet is ~600 days old so lists may have changed. Chat Completions request format and tool_choice behavior not otherwise documented in snippets.

### MiniMax (`minimax`)

- Tier / hosting: Priority 2 / hosted
- Secret: `MINIMAX_API_KEY`
- Base URL: `https://api.minimax.io/v1` (search-summary)
- Auth: bearer via `Authorization` — Authorization: Bearer <key>. China region uses api.minimax.cn / platform.minimaxi.com.
- Models: chat `MiniMax-M2`, reasoning `MiniMax-M2` (search-summary) — M2 family (M2, M2.1, M2.5, M2.7 and highspeed variants, 204,800 ctx) described in docs snippets as historical but still served; M2 series is text and tool-call only. Newer MiniMax-M3 (1M ctx) exists; MiniMax-M3.1-Flash-Preview only via M Plan and MiniMax Code. M2 is a reasoning model, so one id fills both slots; current cheapest id and prices not confirmed.
- Price (chat model, USD per million tokens): input 0.3, output 1.2 (search-summary (round 1, third-party aggregators; not re-confirmed on an official MiniMax page; no official per-token rates found in round 2))
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True, `https://api.minimax.io/anthropic` (search-summary (official docs list an Anthropic-compatible endpoint as recommended; Messages API page platform.minimax.io/docs/api-reference/text-chat-anthropic))
- Verification: **partial**
- Documented deviations:
  - temperature valid range is (0.0, 1.0], 1.0 recommended; values outside return an error (0 not allowed) — probes: `ranges.temperature-2`; expect: unsupported (search-summary)
  - presence_penalty, frequency_penalty, logit_bias and similar OpenAI params are ignored — probes: no probe; expect: unsupported; silent (search-summary)
  - n only supports value 1 — probes: `ranges.n-2`; expect: unsupported (search-summary)
  - Deprecated function_call not supported; use tools — probes: no probe; expect: unsupported (search-summary)
  - Image and audio type inputs are not currently supported (M2 series supports text and tool-call content blocks only) — probes: `images.image-part`, `content-shape.content-parts`; expect: unsupported (search-summary)
- Docs read:
  - https://platform.minimax.io/docs/api-reference/text-openai-api — search-summary, 2026-10-20
  - https://platform.minimax.io/docs/guides/text-generation — search-summary, 2026-10-20
  - https://platform.minimaxi.com/docs/api-reference/text-openai-api — not-reached, 2026-10-20
- Notes: platform.minimax.io and platform.minimaxi.com fail DNS in WebFetch; limits come from search snippets of the official OpenAI SDK page. Whether n>1 errors or is silently ignored is not stated.

### Nebius Token Factory (formerly AI Studio) (`nebius`)

- Tier / hosting: Priority 2 / hosted
- Secret: `NEBIUS_API_KEY`
- Base URL: `https://api.tokenfactory.nebius.com/v1` (search-summary)
- Auth: bearer via `Authorization` — API key as Bearer (OpenAI SDK api_key). Round 1 base URL api.studio.nebius.com/v1 is the older AI Studio host; search results quoting official docs.tokenfactory.nebius.com show https://api.tokenfactory.nebius.com/v1/ as current.
- Models: chat `meta-llama/Meta-Llama-3.1-8B-Instruct-fast`, reasoning `None` (search-summary) — Id seen in the official 'List of models' docs example (search snippet, truncated list). Publisher prefix and casing must be preserved. Docs also use meta-llama/Meta-Llama-3.1-70B-Instruct in examples; third parties use meta-llama/Llama-3.3-70B-Instruct. Verify with GET /v1/models; model catalog at tokenfactory.nebius.com/model-catalog.md.
- Price (chat model, USD per million tokens): input 0.03, output 0.09 (search-summary (docs list-of-models example: prompt 0.00000003, completion 0.00000009 per token for Meta-Llama-3.1-8B-Instruct-fast; list truncated, non-fast price not found))
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not checked)
- Verification: **unverified**
- Documented deviations: none recorded
- Docs read:
  - https://docs.tokenfactory.nebius.com/api-reference/introduction.md — not-reached, 2026-10-20
  - https://docs.nebius.com/studio/inference/api — not-reached, 2026-10-20
  - https://docs.tokenfactory.nebius.com/api-reference — search-summary, 2026-10-20
  - https://docs.tokenfactory.nebius.com/api-reference/examples/list-of-models — search-summary, 2026-10-20
- Notes: docs.nebius.com and docs.tokenfactory.nebius.com fail DNS in the sandbox, so nothing was read directly. Provider rebranded to Nebius Token Factory. No documented deviation list found in snippets.

### SambaNova Cloud (SambaCloud) (`sambanova`)

- Tier / hosting: Priority 2 / hosted
- Secret: `SAMBANOVA_API_KEY`
- Base URL: `https://api.sambanova.ai/v1` (search-summary)
- Auth: bearer via `Authorization` — Authorization: Bearer <key>
- Models: chat `Meta-Llama-3.3-70B-Instruct`, reasoning `gpt-oss-120b` (search-summary) — Search snippets of the SambaCloud supported-models and pricing pages list production models DeepSeek-V3.1, Meta-Llama-3.3-70B-Instruct, gpt-oss-120b (128k ctx) and preview MiniMax-M3, DeepSeek-V3.2, gemma-4-31B-it. Meta-Llama-3.1-8B-Instruct (round 1) was not found in any current listing and may be retired. gpt-oss-120b is cheaper than Llama 3.3 70B but is a reasoning model, so Llama 3.3 70B (used in the quickstart) fills the plain chat slot. Verify with models list.
- Price (chat model, USD per million tokens): input 0.6, output 1.2 (search-summary (cloud.sambanova.ai pricing page snippet: Meta-Llama-3.3-70B-Instruct 0.60/1.20; gpt-oss-120b 0.22/0.59; DeepSeek-V3.1 3/4.50))
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not checked)
- Verification: **partial**
- Documented deviations:
  - Older doc version: logprobs, top_logprobs, n, presence_penalty, frequency_penalty, logit_bias, seed listed as not yet supported and will be ignored — probes: `ranges.logprobs`, `ranges.n-2`, `ranges.seed`; expect: unsupported; silent (search-summary)
  - Newer doc version: only presence_penalty and frequency_penalty ignored; n supported with values 1 to 8; seed supported on SambaStack and SambaCloud for text generation models; logprobs not mentioned — probes: `ranges.n-2`, `ranges.seed`, `ranges.logprobs`; expect: compliant (search-summary)
  - n greater than 1 is not supported together with function calling/tools and returns a 400 error — probes: `ranges.n-2`; expect: adjustment-needed (search-summary)
  - presence_penalty and frequency_penalty ignored in both doc versions — probes: no probe; expect: unsupported; silent (search-summary)
  - temperature accepts 0 to 1 (OpenAI 0 to 2) — probes: `ranges.temperature-2`; expect: adjustment-needed (search-summary)
  - Adds non-OpenAI top_k parameter (round-1 snippet; not re-seen in round 2) — probes: no probe; expect: compliant (search-summary)
  - POST /v1/responses exposed as OpenAI Responses compatible (round-1 snippet; not re-seen in round 2) — probes: `responses.responses-basic`; expect: compliant (search-summary)
- Docs read:
  - https://docs.sambanova.ai/cloud/docs/capabilities/openai-compatibility — not-reached, 2026-10-20
  - https://docs.sambanova.ai/docs/en/features/openai-compatibility.md — not-reached, 2026-10-20
  - https://docs-prod.sambanova.ai/docs/en/features/openai-compatibility.md — not-reached, 2026-10-20
  - https://docs.sambanova.ai/docs/en/features/openai-compatibility — search-summary, 2026-10-20
  - https://docs.sambanova.ai/docs/en/models/sambacloud-models — search-summary, 2026-10-20
  - https://cloud.sambanova.ai/plans/pricing — search-summary, 2026-10-20
- Notes: All docs hosts fail DNS (docs.sambanova.ai, docs-prod.sambanova.ai); facts from search snippets, and doc versions conflict (kept both, treat probes as authoritative). Rate limits per snippet: gpt-oss-120b 60 RPM, Llama 3.3 70B 240 RPM, developer tier capped at 20M tokens/day. Community report of 404 'model does not exist' via OpenAI client for some accounts. A third-party adapter note (llama-stack, pipecat) claiming unsupported params is not SambaNova's and was not used as a deviation.

## Priority 2 · local

### Hugging Face TGI (`tgi`)

- Tier / hosting: Priority 2 / local
- Secret: none (local host)
- Base URL: `http://127.0.0.1:8080/v1` (official-page)
- Auth: bearer via `Authorization` — Local TGI needs no auth; docs use placeholder api_key '-'. Hugging Face Inference Endpoints require an hf_ token and base_url must end in v1/. Quicktour maps -p 8080:80; Messages API page uses localhost:3000 (port is whatever you publish).
- Models: chat `teknium/OpenHermes-2.5-Mistral-7B`, reasoning `None` (official-page) — Quicktour example model (GPU). Docs examples send model 'tgi'. Chat Completions requires a model with a chat template. Too large for CPU CI; see local.small_chat_model.
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not documented on pages read)
- Verification: **partial**
- Not run in CI: text-generation-inference is in maintenance mode and documents a GPU-only start command; no CPU image documented
- Local host: image `ghcr.io/huggingface/text-generation-inference:3.3.5`; CPU image available: None; reasoning fits a CI runner: None (image tag and GPU start_cmd from official quicktour (fetched); no CPU image or command documented so cpu_image_available unknown. small_chat_model is a memory suggestion (not verified).)
- Documented deviations:
  - Messages API is described as fully compatible with the OpenAI Chat Completion API; available from TGI 1.4.0; no parameter list or limitations given — probes: no probe; expect: compliant (official-page)
  - Chat Completions works only for models that have a chat template — probes: no probe; expect: adjustment-needed (official-page)
  - Tool use is implemented via guidance (grammars) on /chat/completion with tools: tools are a special case of grammars that let the model choose one or none of the provided tools — probes: `tools.tool-required`, `structured-output.json-schema-strict`; expect: adjustment-needed (official-page)
  - TGI is in maintenance mode (minor bug fixes, docs, lightweight maintenance only); docs recommend vLLM, SGLang, llama.cpp or MLX going forward — probes: no probe; expect: adjustment-needed (official-page)
  - Docs examples only exercise model, messages, stream, max_tokens; other fields undocumented on the Messages API page — probes: `output-cap.max-tokens`; expect: compliant (official-page)
- Docs read:
  - https://huggingface.co/docs/text-generation-inference/index — fetched, 2026-10-20
  - https://huggingface.co/docs/text-generation-inference/messages_api — fetched, 2026-10-20
  - https://huggingface.co/docs/text-generation-inference/quicktour — fetched, 2026-10-20
  - https://huggingface.co/docs/text-generation-inference/conceptual/guidance — fetched, 2026-10-20
  - https://huggingface.co/docs/text-generation-inference/reference/api_reference — fetched, 2026-10-20
  - https://huggingface.github.io/text-generation-inference/ — not-reached, 2026-10-20
- Notes: Downgraded from verified: docs pages assert 'fully compatible' but list no parameters (the OpenAPI reference at huggingface.github.io is unreachable from the sandbox), so parameter-level support is unconfirmed. MAINTENANCE: index page caution says TGI is in maintenance mode. Quicktour exposes native /generate as well. Hardware guides: Nvidia, AMD, Intel GPUs, Gaudi, Inferentia; no CPU guide or CPU image documented. Index feature list mentions log probabilities, stop sequences, logits warpers (temperature, top-p, top-k, repetition penalty).

## Priority 2 · first-party-compat

### Anthropic OpenAI SDK compatibility (`anthropic-openai`)

- Tier / hosting: Priority 2 / first-party-compat
- Secret: `ANTHROPIC_API_KEY`
- Base URL: `https://api.anthropic.com/v1` (official-page)
- Auth: bearer via `Authorization` — Claude API key as OpenAI SDK api_key. Quickstart base_url is https://api.anthropic.com/v1/ (trailing slash). Personal or service-account keys with access to multiple workspaces must also send the anthropic-workspace-id header on every request.
- Models: chat `claude-haiku-5-5`, reasoning `claude-sonnet-4-6` (official-page) — claude-haiku-5-5 is the cheapest current model on the models overview page (fastest, adaptive thinking, from $0.10/$0.50 per MTok). Because thinking is adaptive on 5.x models and thinking.enabled+budget_tokens is not accepted on later models, the reasoning slot uses claude-sonnet-4-6 (legacy, still available), which is the model in the compat page's thinking example. Quickstart uses claude-opus-5-5. Current IDs: claude-fable-5-1, claude-opus-5-5, claude-sonnet-5-5, claude-haiku-5-5. Legacy still available: Fable 5, Opus 5, Opus 4.8/4.7/4.6/4.5, Sonnet 5, Sonnet 4.6, Haiku 4.5.
- Price (chat model, USD per million tokens): input 0.1, output 0.5 (official-page (platform.claude.com pricing + models overview, Claude Haiku 5.5, prompts up to 100,000 tokens; prompts over 100,000 tokens $0.50/$2.50). Sonnet 5.5 $2/$10, Opus 5.5 $4/$20, Fable 5.1 $10/$50, Haiku 4.5 $1/$5.)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True, `https://api.anthropic.com/v1/messages` (official-page (compat page says rate limits follow /v1/messages; models/pricing pages describe the native Messages API))
- Verification: **verified**
- Documented deviations:
  - Layer is primarily for testing/comparison, not a long-term or production-ready solution for most use cases; most unsupported fields are silently ignored rather than producing errors — probes: no probe; expect: adjustment-needed; silent (official-page)
  - The strict parameter for function calling is ignored; tool-use JSON is not guaranteed to follow the supplied schema (tools[n].function.strict) — probes: `structured-output.json-schema-strict`; expect: unsupported; silent (official-page)
  - functions[n].strict (deprecated functions field) ignored; name, description, parameters fully supported — probes: `structured-output.json-schema-strict`; expect: unsupported; silent (official-page)
  - response_format is ignored; for JSON output use Structured Outputs on the native Claude API — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: unsupported; silent (official-page)
  - Audio input (content part input_audio) is ignored and stripped from input — probes: `content-shape.content-parts`; expect: adjustment-needed; silent (official-page)
  - User content part type file is ignored — probes: `content-shape.content-parts`; expect: adjustment-needed; silent (official-page)
  - Assistant content part type refusal is ignored; assistant audio and refusal message fields ignored — probes: `content-shape.content-parts`; expect: adjustment-needed; silent (official-page)
  - User content parts: string content and array text parts fully supported (assistant string/text array, tool string/text array also fully supported) — probes: `content-shape.content-parts`; expect: compliant (official-page)
  - image_url part: url fully supported, detail ignored — probes: `images.image-part`; expect: compliant; silent (official-page)
  - Prompt caching is not supported on the compat layer (supported in Anthropic SDKs); usage.prompt_tokens_details always empty — probes: `cached-tokens.cached-tokens`; expect: unsupported (official-page)
  - usage.completion_tokens_details always empty — probes: `cached-tokens.cached-tokens`, `reasoning.reasoning-shape`; expect: unsupported (official-page)
  - System/developer messages are hoisted and concatenated with a single newline into the top-level system field, ahead of every message, wherever they appear — probes: `roles.developer-role`, `roles.two-system`; expect: adjustment-needed (official-page)
  - developer role: content fully supported but hoisted — probes: `roles.developer-role`; expect: adjustment-needed (official-page)
  - system role: content fully supported but hoisted — probes: `roles.two-system`; expect: adjustment-needed (official-page)
  - name ignored on developer messages — probes: `roles.name-field`; expect: unsupported; silent (official-page)
  - name ignored on system messages — probes: `roles.name-field`; expect: unsupported; silent (official-page)
  - name ignored on user messages — probes: `roles.name-field`; expect: unsupported; silent (official-page)
  - name ignored on tool messages — probes: `roles.name-field`; expect: unsupported; silent (official-page)
  - name ignored on function messages — probes: `roles.name-field`; expect: unsupported; silent (official-page)
  - Thinking is enabled by adding the thinking parameter (extra_body); on current models thinking is adaptive and on Claude 5 models it is on by default; manual extended thinking (type enabled + budget_tokens) is a legacy mode — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - The OpenAI SDK does not return Claude's thinking; use the native Claude API for thinking output and summarized thinking — probes: `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: unsupported (official-page)
  - reasoning_effort is ignored — probes: `reasoning.reasoning-effort`; expect: unsupported; silent (official-page)
  - Models overview: extended thinking (thinking.type enabled + budget_tokens) is deprecated on Opus 4.6 and Sonnet 4.6 and not accepted on later models — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - temperature between 0 and 1 inclusive; values greater than 1 are capped at 1 — probes: `ranges.temperature-2`; expect: adjustment-needed (official-page)
  - On Claude 4.7 and later models and Claude Mythos Preview any temperature value below 1 returns a 400 error (omit it) — probes: `ranges.temperature-2`; expect: adjustment-needed (official-page)
  - top_p passed through to the model; Claude 4.7+ and Mythos Preview return 400 for any value other than 0.99; a request that sets both temperature and top_p also returns 400 — probes: no probe; expect: adjustment-needed (official-page)
  - n must be exactly 1; choices[] always has length 1 — probes: `ranges.n-2`; expect: unsupported (official-page)
  - logprobs ignored; response logprobs always empty — probes: `ranges.logprobs`; expect: unsupported; silent (official-page)
  - top_logprobs ignored — probes: `ranges.logprobs`; expect: unsupported; silent (official-page)
  - seed ignored — probes: `ranges.seed`; expect: unsupported; silent (official-page)
  - stop: all non-whitespace stop sequences work — probes: `ranges.stop-5`; expect: compliant (official-page)
  - metadata ignored — probes: no probe; expect: unsupported; silent (official-page)
  - prediction ignored — probes: no probe; expect: unsupported; silent (official-page)
  - presence_penalty ignored — probes: no probe; expect: unsupported; silent (official-page)
  - frequency_penalty ignored — probes: no probe; expect: unsupported; silent (official-page)
  - service_tier ignored; response service_tier always empty — probes: no probe; expect: unsupported; silent (official-page)
  - audio (request) ignored; response choices[].message.audio always empty — probes: no probe; expect: unsupported; silent (official-page)
  - logit_bias ignored — probes: no probe; expect: unsupported; silent (official-page)
  - store ignored — probes: no probe; expect: unsupported; silent (official-page)
  - user ignored — probes: no probe; expect: unsupported; silent (official-page)
  - modalities ignored — probes: no probe; expect: unsupported; silent (official-page)
  - max_tokens fully supported — probes: `output-cap.max-tokens`; expect: compliant (official-page)
  - max_completion_tokens fully supported — probes: `output-cap.max-completion-tokens`; expect: compliant (official-page)
  - stream fully supported; tool_calls (assistant and choices[].message) fully supported — probes: `tool-streaming.tool-stream`; expect: compliant (official-page)
  - stream_options fully supported — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - parallel_tool_calls fully supported — probes: no probe; expect: compliant (official-page)
  - tool_choice supported; required and named-function values force a tool call, but Claude Opus 5.5, Sonnet 5.5, Fable 5.1 and Mythos 5.1 do not support forced tool use and return an error, so use auto on those models — probes: `tools.tool-required`; expect: unsupported (official-page)
  - tools[n].function name, description, parameters fully supported; assistant function_call and tool_call_id fully supported — probes: no probe; expect: compliant (official-page)
  - Response fields system_fingerprint and refusal always empty — probes: no probe; expect: unsupported (official-page)
  - Response model, id, object, created, finish_reason, usage prompt/completion/total tokens fully supported — probes: `model-identity.model-identity`; expect: compliant (official-page)
  - Error format is consistent with OpenAI but detailed error messages are not equivalent; use only for logging and debugging — probes: `errors.error-shape`; expect: compliant (official-page)
  - Rate limits follow Anthropic standard limits for /v1/messages; headers x-ratelimit-limit/remaining/reset-requests and -tokens, retry-after, request-id fully supported — probes: `errors.rate-limit-headers`; expect: compliant (official-page)
  - openai-version header always 2020-10-01; openai-processing-ms always empty — probes: `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
- Docs read:
  - https://platform.claude.com/docs/en/api/openai-sdk — fetched, 2026-10-20
  - https://platform.claude.com/docs/en/about-claude/models/overview — fetched, 2026-10-20
  - https://platform.claude.com/docs/en/about-claude/pricing — fetched, 2026-10-20
- Notes: Compat page (redirects to /cli-sdks-libraries/libraries/openai-sdk) read in full on 2026-10-20: every request-field, tools/functions, per-role message, response-field, error and header table row is mapped above, one entry per row or tightly coupled rows. Page documents no responses endpoint and no models list on the compat layer. The page table oddly lists tool_choice under the tool and function role tabs. reasoning-shape: thinking is not returned through the SDK. Paid key required, no free tier known.

### Gemini OpenAI compatibility (`gemini-openai`)

- Tier / hosting: Priority 2 / first-party-compat
- Secret: `GEMINI_API_KEY`
- Base URL: `https://generativelanguage.googleapis.com/v1beta/openai` (official-page)
- Auth: bearer via `Authorization` — Gemini API key as OpenAI client api_key or Authorization: Bearer. Docs examples use trailing slash (.../v1beta/openai/). The caching example uses a different base URL (/v1beta/ without /openai/) and a different key variable name; treat as doc inconsistency.
- Models: chat `gemini-3.1-flash-lite`, reasoning `gemini-3.8-flash` (official-page) — Models page (2026-10-20) lists Stable: gemini-4-argon, gemini-3.8-flash, gemini-3.6-flash, gemini-3.5-flash-lite, gemini-3.1-flash-lite; Preview: gemini-3-flash-preview, gemini-3.1-pro-preview; Legacy access-limited (still available to previous users, not recommended for new projects): gemini-2.5-flash, gemini-2.5-flash-lite, gemini-2.5-pro; shut down: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-3.1-flash-lite-preview, gemini-3-pro-preview. gemini-3.1-flash-lite is the cheapest stable model not marked legacy; gemini-2.5-flash-lite ($0.10/$0.40) is cheaper but legacy and access-limited. gemini-3.8-flash is the chat example model on the compat page (and has thinking). reasoning_effort none works only on 2.5 models.
- Price (chat model, USD per million tokens): input 0.25, output 1.5 (official-page (ai.google.dev pricing, gemini-3.1-flash-lite paid tier, text/image/video input; audio input $0.50; free tier available). Others: gemini-3.5-flash-lite $0.30/$2.50; gemini-2.5-flash-lite $0.10/$0.40 (legacy); gemini-3.8-flash $0.75/$3.75 until 2026-12-31 then $1.50/$7.50.)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not mentioned on compat page)
- Verification: **verified**
- Documented deviations:
  - Support for the OpenAI libraries is still in beta while feature support is extended (Current limitations); page recommends the Gemini API directly if not already using OpenAI libraries — probes: no probe; expect: adjustment-needed (official-page)
  - Page says to choose a compatible Gemini model but lists none; per-model behavior differs (reasoning configs differ per model) — probes: no probe; expect: adjustment-needed (official-page)
  - reasoning_effort maps to thinking_level on Gemini 4 Argon, 3.1 Flash-Lite and 3 Flash, and to thinking_budget on 2.5 models; on Gemini 4 Argon minimal maps to low; on 2.5 minimal and low both map to 1,024 tokens, medium 8,192, high 24,576 — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - If no reasoning_effort is specified Gemini uses the model's default — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - reasoning_effort none disables thinking only on 2.5 models; reasoning cannot be turned off for Gemini 2.5 Pro or 3 models — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - reasoning_effort and thinking_level/thinking_budget overlap in functionality and cannot be used at the same time — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - Thinking configuration and thought summaries are enabled via extra_body google thinking_config (thinking_level, include_thoughts), corresponding to Gemini ThinkingConfig — probes: `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - Gemini 3 supports OpenAI compatibility for thought signatures in chat completion APIs — probes: `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Gemini context cache is exposed only through extra_body cached_content (corresponds to Gemini's general content cache); no OpenAI-style cached_tokens behavior documented — probes: `cached-tokens.cached-tokens`; expect: adjustment-needed (official-page)
  - service_tier accepts priority or flex; when not explicitly assigned it defaults to standard, equivalent to default for OpenAI — probes: no probe; expect: adjustment-needed (official-page)
  - Image generation: only prompt, model, n, size, response_format (plus extra_body) supported at top level; any other parameters are silently ignored — probes: no probe; expect: unsupported; silent (official-page)
  - Image generation grounding with Google Search is only available on Gemini 3 and newer models; extra_body tools only for gemini-3-pro-image-preview — probes: no probe; expect: unsupported (official-page)
  - Video generation: supported top-level parameters are only prompt and model; duration_seconds, image, aspect_ratio etc. must go in extra_body; long-running operation returning an operation ID to poll; text says Sora-compatible /v1/videos but code uses /v1beta/openai/videos; 1080p and 4K trigger upsampler pipeline; duration_seconds must be 8 with reference_images, interpolation or extension; aspect_ratio maps from size if unspecified — probes: no probe; expect: adjustment-needed (official-page)
  - Batch: creating a batch, monitoring status and viewing results supported; file upload and download not supported through the compat layer (use the genai client) — probes: no probe; expect: unsupported (official-page)
  - Embeddings: gemini-embedding-2-preview for multimodal embeddings or gemini-embedding-001 for text-only — probes: no probe; expect: compliant (official-page)
  - Audio input via curl may hit Argument list too long if the base64 audio is large (client-side note) — probes: no probe; expect: compliant (official-page)
- Docs read:
  - https://ai.google.dev/gemini-api/docs/openai — fetched, 2026-10-20
  - https://ai.google.dev/gemini-api/docs/pricing — fetched, 2026-10-20
  - https://ai.google.dev/gemini-api/docs/models — fetched, 2026-10-20
- Notes: Compat page has no table of unsupported chat-completions request fields. It does not document seed, logprobs, n, stop, response_format/json_schema behavior, tool_choice, developer role, system-role handling, stream_options usage, max_tokens vs max_completion_tokens, or error and rate-limit header shape, so those probes are undocumented (probe them; no expectation recorded). Page examples show: chat (streaming, function calling, image and audio input, structured output via parse), images, videos, embeddings, batch, models list/retrieve. Page sample code has typos and inconsistent extra_body nesting (extra_body inside extra_body). Free tier exists; CI-feasible with a key. Pricing page says free-tier content may be used to improve Google products.

## Priority 3 · hosted

### Featherless AI (`featherless`)

- Tier / hosting: Priority 3 / hosted
- Secret: `FEATHERLESS_API_KEY`
- Base URL: `https://api.featherless.ai/v1` (official-page)
- Auth: bearer via `Authorization` — Bearer FEATHERLESS_API_KEY from featherless.ai/account/api-keys. The overview prose writes the header name as 'Authentication: Bearer ...' (apparent typo) while every code example uses Authorization: Bearer. Optional HTTP-Referer and X-Title attribution headers requested for integrations.
- Models: chat `meta-llama/Llama-3.1-8B-Instruct`, reasoning `Qwen/Qwen3-32B` (official-page) — Both ids appear verbatim in official doc request examples (Llama-3.1-8B-Instruct in the chat_template_kwargs guide, Qwen/Qwen3-32B in the tool-calling and chat_template_kwargs guides). Availability and warm/cold state could not be checked because /v1/models (documented as callable unauthenticated) is refused by the sandbox proxy. Round 1 id meta-llama/Meta-Llama-3.1-8B-Instruct was not found on any fetched page and is dropped. Qwen3 thinking defaults to true and is toggled with chat_template_kwargs.enable_thinking; cheapest model cannot be determined (no per-token catalog price).
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (not listed in docs index links (overview page nav))
- Verification: **verified**
- Documented deviations:
  - seed: 'Random seed for generation. (Not reliable, as we use multiple servers)' — probes: `ranges.seed`; expect: adjustment-needed (official-page)
  - Documented chat request body: model, messages, presence_penalty, frequency_penalty, repetition_penalty, temperature, top_p, top_k, min_p, seed, stop, stop_token_ids, include_stop_str_in_output, max_tokens, min_tokens, chat_template_kwargs. max_completion_tokens, n, logprobs, response_format, stream_options are not listed (tools documented on a separate guide) — probes: `output-cap.max-completion-tokens`, `ranges.n-2`, `ranges.logprobs`, `structured-output.json-schema-strict`, `structured-output.json-object`, `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
  - Non-OpenAI request fields documented: repetition_penalty, top_k, min_p, stop_token_ids, include_stop_str_in_output, min_tokens, chat_template_kwargs — probes: no probe; expect: compliant (official-page)
  - max_tokens above a model's max_completion_tokens (from /v1/models) is clamped to that value — probes: `output-cap.max-tokens`; expect: adjustment-needed (official-page)
  - stop: array of strings; returned output excludes the stop strings (no count limit documented) — probes: `ranges.stop-5`; expect: compliant (official-page)
  - Native function calling documented only for moonshotai/Kimi-K2-Instruct and the Qwen 3 family; other models need the guide's workaround. tool_choice is not documented — probes: `tools.tool-required`, `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
  - Reasoning is toggled through chat_template_kwargs (enable_thinking / thinking / do_reasoning synonyms, thinking_budget, preserve_thinking, clear_thinking) per model family; always-on models (DeepSeek R1, Kimi-K2-Thinking, MiniMax-M2, gpt-oss, Step-3.5) are not toggleable; no reasoning_effort or reasoning object documented. Not every model supports toggling; unknown keys are accepted and ignored if the template does not use them — probes: `reasoning.reasoning-effort`, `reasoning.reasoning-shape`; expect: adjustment-needed; silent (official-page)
  - Documented response body contains only id, object, created, model, choices[].message{role,content}, finish_reason and usage{prompt_tokens, completion_tokens, total_tokens}; no cached_tokens or reasoning_tokens details documented — probes: `cached-tokens.cached-tokens`; expect: adjustment-needed (official-page)
  - Error codes documented: 400 model cold/not ready (cold models must be warmed, minutes to an hour), 401 unauthenticated, 403 gated model or not on plan, 429 when requests exceed the plan's concurrent-unit budget, 500, 503 insufficient capacity (retry up to 3 times); error body shape not documented — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
- Docs read:
  - https://featherless.ai/docs/overview — fetched, 2026-10-20
  - https://featherless.ai/docs/api-overview-and-common-options — fetched, 2026-10-20
  - https://featherless.ai/docs/completions — fetched, 2026-10-20
  - https://featherless.ai/docs/tool-calling — fetched, 2026-10-20
  - https://featherless.ai/docs/chat-template-kwargs — fetched, 2026-10-20
  - https://featherless.ai/docs/api-reference-models — fetched, 2026-10-20
  - https://featherless.ai/docs/api-reference-error-codes — fetched, 2026-10-20
  - https://featherless.ai/docs/concurrency — fetched, 2026-10-20
  - https://featherless.ai/docs/plans — fetched, 2026-10-20
  - https://api.featherless.ai/v1/models — not-reached, 2026-10-20
- Notes: Docs say the API is 'designed to be API compatible with OpenAI' and call it a living spec. Pricing is plan-based, not per-token: Chat plan $25/month (4 concurrent units, 32K context, any catalog model); Developer from $50 credits/month and Pay-As-You-Go are credit-based (usage deducted by model price and tokens, 100 concurrent units, up to 256K context). Concurrent-unit cost by size: <16B = 1, <35B = 2, 70B+ = 4; requests above the unit budget get HTTP 429. Round 1 said plan tiers 'Basic/Premium/Scale'; current plans page lists Developer, Pay-As-You-Go, Chat and Business (dedicated GPUs). A free-tier CI run is not possible; parallel probes risk 429.

### Hyperbolic (`hyperbolic`)

- Tier / hosting: Priority 3 / hosted
- Secret: `HYPERBOLIC_API_KEY`
- Base URL: `https://api.hyperbolic.xyz/v1` (search-summary)
- Auth: bearer via `Authorization` — Authorization: Bearer <key> (search summary of the quickstart)
- Models: chat `meta-llama/Llama-3.3-70B-Instruct`, reasoning `None` (search-summary) — Search summary of the quickstart/integration pages names meta-llama/Llama-3.3-70B-Instruct (integration examples) and deepseek-ai/DeepSeek-V3 (quickstart) as example ids. Not confirmed against a live models page, so availability and the cheapest chat model are unknown. Round 1 reasoning id deepseek-ai/DeepSeek-R1 could not be re-confirmed and is removed; a Hyperbolic blog (search result title) mentions DeepSeek R1-0528 availability but the API id was not seen.
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered None (search-summary)
- Verification: **partial**
- Documented deviations: none recorded
- Docs read:
  - https://docs.hyperbolic.xyz/docs/rest-api — not-reached, 2026-10-20
  - https://docs.hyperbolic.xyz/docs/inference-api — not-reached, 2026-10-20
  - https://www.hyperbolic.ai/docs/inference/quickstart — search-summary, 2026-10-20
  - https://www.hyperbolic.ai/docs/inference/integrations — search-summary, 2026-10-20
- Notes: Round 2: all Hyperbolic hosts (docs.hyperbolic.xyz, www.hyperbolic.ai, hyperbolic.ai, api.hyperbolic.xyz, app.hyperbolic.xyz) are refused by the sandbox proxy (CONNECT 403) or fail DNS in WebFetch, so nothing was read directly. Search summary: quickstart chat endpoint is https://api.hyperbolic.xyz/v1/chat/completions, described as OpenAI-compatible; integrations page says the same base URL works for LangChain/LlamaIndex. A Hyperbolic blog (about a year old) says serverless API costs $0.10-$0.40 per Mtok across models; no per-model price. No documented deviations found.

### Lambda Inference API (`lambda`)

- Tier / hosting: Priority 3 / hosted
- Secret: `LAMBDA_API_KEY`
- Base URL: `https://api.lambda.ai/v1` (search-summary)
- Auth: bearer via `Authorization` — Authorization: Bearer <key> (search summary; moot if the API is retired)
- Models: chat `None`, reasoning `None` (search-summary) — No models recorded: the Inference API documentation is no longer on docs.lambda.ai, so no current model list could be read.
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions None, responses None
- Anthropic Messages endpoint (later phase): offered None (not checked)
- Verification: **unverified**
- Not run in CI: the Lambda Inference API appears to be retired: its docs page redirects to the docs home (not confirmed against api.lambda.ai)
- Documented deviations: none recorded
- Docs read:
  - https://docs.lambda.ai/public-cloud/lambda-inference-api/ — fetched, 2026-10-20
  - https://docs.lambda.ai/sitemap.xml — fetched, 2026-10-20
  - https://docs.lambda.ai/public-cloud/ — fetched, 2026-10-20
  - https://docs.lambda.ai/api/ — fetched, 2026-10-20
  - https://deeptalk.lambda.ai/t/sunsetting-chat-sunsetting-inference/4744 — search-summary, 2026-10-20
  - https://lambda.ai/inference — search-summary, 2026-10-20
  - https://lambda.ai/blog/inference-release — not-reached, 2026-10-20
- Notes: ANSWER: the Lambda Inference API is NOT still offered; treat as retired and exclude from the matrix. Evidence: (1) official docs, fetched 2026-10-20: https://docs.lambda.ai/public-cloud/lambda-inference-api/ now returns a redirect stub to the docs home (<title>Redirecting...</title>, canonical '../'); the docs sitemap.xml and the Public Cloud index contain no Inference API page (only an unrelated 'Deploying a Llama 3 inference endpoint' tutorial); docs.lambda.ai/api/ (cloud API reference) has zero occurrences of the word 'inference' and of api.lambda.ai; (2) search summary: Lambda's DeepTalk forum thread 'Sunsetting Chat == Sunsetting Inference?' (dated 2025-09-02) quotes the notice that Lambda Chat and Lambda Inference / Inference API will be sunset on September 25 (2025), and lambda.ai/inference now says 'as the Inference API winds down, you can continue deploying and scaling models ... on NVIDIA GPU instances'. Round 1's date 2026-05-29 came from a third-party catalog (apis.io) and is not Lambda's shutdown date. Liveness of api.lambda.ai could not be tested (blocked by sandbox proxy); lambda.ai, deeptalk.lambda.ai and lambdalabs.com also unreachable by direct fetch, so the forum/blog statements are search-summary only. Round 1 model id llama-4-maverick-17b-128e-instruct-fp8 removed (unsupportable).

### Novita AI (`novita`)

- Tier / hosting: Priority 3 / hosted
- Secret: `NOVITA_API_KEY`
- Base URL: `https://api.novita.ai/openai` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <token> (novita.ai/llms.txt: 'Authentication: Bearer token via Authorization: Bearer <token>')
- Models: chat `meta-llama/llama-3.1-8b-instruct`, reasoning `zai-org/glm-4.7-flash` (official-page) — Ids read from official novita.ai model detail pages (model-detail/meta-llama-llama-3.1-8b-instruct and model-detail/zai-org-glm-4.7-flash), which show the id in the SDK example and serverless pricing. Llama 3.1 8B: $0.02/$0.05 per Mtok, fp8, 16K context/output, Structured Output supported, serverless. GLM 4.7 Flash: $0.07 in / $0.01 cache read / $0.4 out, features Function Calling, Structured Output, Reasoning. Both appear on novita.ai/pricing and /models. Round 1 id meta-llama/llama-3.3-70b-instruct also exists on the pricing page but is not the cheapest.
- Price (chat model, USD per million tokens): input 0.02, output 0.05 (official-page (novita.ai/models/model-detail/meta-llama-llama-3.1-8b-instruct; also novita.ai/pricing). Reasoning model glm-4.7-flash: 0.07 / 0.4)
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True (official-page (index entry 'LLM Anthropic Compatibility' at docs.novita.ai/guides/llm-anthropic-compatibility.md listed in novita.ai/llms.txt; the guide itself is unreachable so the URL is not confirmed))
- Verification: **partial**
- Documented deviations: none recorded
- Docs read:
  - https://novita.ai/llms.txt — fetched, 2026-10-20
  - https://novita.ai/models — fetched, 2026-10-20
  - https://novita.ai/pricing — fetched, 2026-10-20
  - https://novita.ai/models/model-detail/meta-llama-llama-3.1-8b-instruct — fetched, 2026-10-20
  - https://novita.ai/models/model-detail/zai-org-glm-4.7-flash — fetched, 2026-10-20
  - https://novita.ai/docs/guides/llm-api — not-reached, 2026-10-20
  - https://docs.novita.ai/api-reference/model-apis-llm-create-chat-completion.md — not-reached, 2026-10-20
  - https://docs.novita.ai/guides/llm-anthropic-compatibility.md — not-reached, 2026-10-20
- Notes: docs.novita.ai (the real docs host; novita.ai/docs redirects there) is refused by the sandbox proxy, so the chat-completions API reference and any compatibility/deviation list were NOT read. From novita.ai/llms.txt (official): 'OpenAI-Compatible Base URL: https://api.novita.ai/openai'; the official model pages' OpenAI SDK examples use base_url=https://api.novita.ai/openai. Whether {base}/chat/completions resolves without /v1 or needs https://api.novita.ai/openai/v1 was not testable (api.novita.ai blocked); round 1 saw /openai/v1 in search summaries and docs link-index lists 'Model Apis LLM List Models' (so models_list true) and 'Create Chat Completion'/'Create Completion'. No Responses endpoint appears in the llms.txt index (responses left null). Legacy /v3/openai host is not mentioned anywhere official. Model library lists 87 models today; many serverless.

### Parasail (`parasail`)

- Tier / hosting: Priority 3 / hosted
- Secret: `PARASAIL_API_KEY`
- Base URL: `https://api.parasail.io/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <PARASAIL_API_KEY>; keys from saas.parasail.io/keys; standard keys have full inference access, read-only keys cannot run inference. Docs say do not use legacy Parasail hosts.
- Models: chat `parasail-mistralaimistral-nemo`, reasoning `parasail-gpt-oss-20b` (official-page) — Both ids from the live-generated serverless catalog (docs.parasail.io products/overview/models.md, 'generated daily from the live models endpoint'). Mistral Nemo (FP8): $0.03/$0.03 per Mtok, 131,072 context. gpt-oss-20b (FP4): $0.03/$0.15, 131,072 context; GPT-OSS reasoning controls documented (thinking_budget via custom_params, reasoning_effort low/medium/high via chat_template_kwargs). Doc examples use parasail-deepseek-r1 and parasail-gpt-oss-20b-fast, which are NOT in the current catalog table, so those example ids are stale or unlisted. Other cheap ids: parasail-llama-32-3b-instruct ($0.05/$0.33), parasail-qwen35-9b ($0.10/$0.25).
- Price (chat model, USD per million tokens): input 0.03, output 0.03 (official-page (docs.parasail.io/parasail-docs/products/overview/models.md). Reasoning model parasail-gpt-oss-20b: 0.03 / 0.15)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered None (not listed in docs.parasail.io llms.txt index (no Anthropic/messages page))
- Verification: **verified**
- Documented deviations:
  - Parameter table for chat completions lists max_tokens (default None); doc examples use max_completion_tokens in the Python example but max_tokens in the cURL example; max_completion_tokens is not in the parameter table — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - Parameter reference says Parasail supports all parameters that vLLM supports; non-OpenAI fields such as top_k, repetition_penalty go via extra_body — probes: no probe; expect: compliant (official-page)
  - Message roles table lists only system, user and assistant (tool-calling guide separately documents tool results); developer role and the name field are not listed — probes: `roles.developer-role`, `roles.name-field`; expect: adjustment-needed (official-page)
  - Documented parameter table omits n, logprobs, stop, stream_options, response_format (response_format json_schema documented separately in the structured-output guide), max_completion_tokens — probes: `ranges.n-2`, `ranges.logprobs`, `ranges.stop-5`, `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
  - Structured output: guided_json (via extra_body) or response_format type json_schema accepted; support is model-dependent (table of models 'as of June 2026'; docs advise validating with a small test request); json_object not mentioned — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: adjustment-needed (official-page)
  - Reasoning controls are model-specific via extra_body: DeepSeek V3.1 chat_template_kwargs.thinking, Qwen3.5 chat_template_kwargs.enable_thinking, GPT-OSS custom_params.thinking_budget and chat_template_kwargs.reasoning_effort (low/medium/high); no top-level reasoning_effort or reasoning object documented — probes: `reasoning.reasoning-effort`, `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - Responses API: store must be false on every request; omitting it returns an error; store true is not supported — probes: `responses.responses-store`, `responses.responses-basic`; expect: unsupported (official-page)
  - Responses API: previous_response_id (stateful chaining) not supported; pass full history in input. Responses on Dedicated Instances not documented — probes: `responses.responses-store`; expect: unsupported (official-page)
  - parasail-qwen25-vl-72b-instruct emits tool calls reliably only with tool_choice required; with auto it tends to answer in prose — probes: `tools.tool-required`; expect: adjustment-needed (official-page)
  - Chat Completions response documented with usage = prompt_tokens, completion_tokens, total_tokens only; no cached_tokens or reasoning_tokens details documented (catalog lists a separate cached-input price) — probes: `cached-tokens.cached-tokens`, `reasoning.reasoning-shape`; expect: adjustment-needed (official-page)
  - Errors use OpenAI-compatible status codes with a JSON body error object (message, type, code): 400, 401, 403, 404, 429, 500 — probes: `errors.error-shape`; expect: compliant (official-page)
- Docs read:
  - https://docs.parasail.io/parasail-docs/llms.txt — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/api-reference/chat-completions.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/api-reference/authentication.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/api-reference/parameters.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/api-reference/models-endpoint.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/api-reference/responses-api.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/products/overview/models.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/products/overview/model-specific-notes.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/products/overview/responses-api.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/guides/structured-output.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/guides/tool-function-calling.md — fetched, 2026-10-20
  - https://docs.parasail.io/parasail-docs/operate-in-production/limits-and-quotas.md — fetched, 2026-10-20
- Notes: Endpoint matrix (chat-completions page): POST /v1/chat/completions, /v1/completions, /v1/embeddings, GET /v1/models serve Serverless and Dedicated; POST /v1/responses Serverless yes, Dedicated 'Not documented'. Rate limits (limits-and-quotas): Serverless Free 5 RPM, Serverless User 500 RPM, Enterprise unlimited, token limits not enforced; rate-limit response headers are not documented. Streaming ends with data: [DONE]. Free tier at 5 RPM makes parallel CI probes risky. Round 1 note that Responses was 'ambiguous' is resolved: documented as supported with store=false.

### Snowflake Cortex (`snowflake`)

- Tier / hosting: Priority 3 / hosted
- Secret: `SNOWFLAKE_API_KEY`
- Base URL: `https://${SNOWFLAKE_ACCOUNT}.snowflakecomputing.com/api/v2/cortex/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <token> where token is a JWT, OAuth token or programmatic access token (PAT); the quickstart uses a PAT. Optional X-Snowflake-Authorization-Token-Type (KEYPAIR_JWT, OAUTH, PROGRAMMATIC_ACCESS_TOKEN) and Accept: application/json, text/event-stream. The calling user's default role must hold SNOWFLAKE.CORTEX_USER (granted to PUBLIC by default) or SNOWFLAKE.CORTEX_REST_API_USER.
- Models: chat `openai-gpt-5-nano`, reasoning `openai-gpt-5-mini` (official-page) — Model naming: plain Snowflake catalog names with a family prefix, e.g. openai-gpt-5, openai-gpt-5-nano, claude-sonnet-4-5, llama3.1-8b, mistral-large2, deepseek-r1, snowflake-llama-3.3-70b; fine-tuned models use database.schema.model. Not provider/model and no version dates. openai-gpt-5-nano and openai-gpt-5-mini are on the model availability table (AWS cross-region US etc.) and the rate-limit table (nano 10,000,000 TPM / 10,000 RPM; mini 2,000,000 TPM / 2,000 RPM). OpenAI-family models are the most compliant (see deviations); reasoning_effort (none, minimal, low, medium, high) works only for OpenAI models; Claude uses the reasoning object. Cheapest model cannot be determined: pricing is in credits via the Snowflake Service Consumption Table (not fetched). Availability is per region and cross-region setting; * = public preview, ** = private preview.
- Price: unknown; the runner's fallback price is used for the spend estimate
- Endpoints: chat completions True, responses None
- Anthropic Messages endpoint (later phase): offered True, `https://<account-identifier>.snowflakecomputing.com/api/v2/cortex/v1/messages` (official-page (Cortex REST API page: Messages API, Claude models only; Anthropic SDK base_url is .../api/v2/cortex and needs the Authorization Bearer header overridden))
- Verification: **verified**
- Documented deviations:
  - max_tokens is deprecated and returns an error for OpenAI, Claude and other model families; use max_completion_tokens — probes: `output-cap.max-tokens`; expect: unsupported (official-page)
  - max_completion_tokens supported for all families; defaults to 4096 when unset; theoretical maximum 131,072; each model has its own output limit (rate-limit table lists 16,384) — probes: `output-cap.max-completion-tokens`; expect: compliant (official-page)
  - messages[].role: OpenAI models supported; Claude and other models only user, assistant, system (developer role not supported for them) — probes: `roles.developer-role`; expect: adjustment-needed (official-page)
  - messages[].name supported for OpenAI models, ignored for Claude and other models — probes: `roles.name-field`; expect: adjustment-needed; silent (official-page)
  - Image content parts: image_url supported for OpenAI and Claude models, error for other models; images must be base64-encoded, max 20 per conversation, 20 MiB max request size; image input for claude-sonnet-4-5 and newer, openai-gpt-4.1 and newer — probes: `images.image-part`; expect: adjustment-needed (official-page)
  - Content parts type text supported for all; file content part errors for OpenAI and Claude (ignored for other); input_audio errors for OpenAI (ignored for others); audio and messages[].audio error for OpenAI models, ignored for others; audio not supported — probes: `content-shape.content-parts`; expect: adjustment-needed (official-page)
  - tools: supported for OpenAI models, only function tools for Claude models, error for other models; tool calling supported for OpenAI and Claude models only — probes: `tools.tool-required`, `tool-streaming.tool-stream`; expect: adjustment-needed (official-page)
  - tool_choice supported for OpenAI models, only function tools for Claude, ignored for other models; parallel_tool_calls supported for OpenAI only (ignored otherwise) — probes: `tools.tool-required`; expect: adjustment-needed; silent (official-page)
  - response_format: supported for OpenAI models; Claude models only json_schema; ignored for other models; json_schema is the documented structured-output form — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: adjustment-needed; silent (official-page)
  - stream_options.include_usage supported for all families; stream_options.include_obfuscation ignored; default usage behavior not documented — probes: `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: compliant (official-page)
  - usage.prompt_tokens_details.cached_tokens: OpenAI models report cache reads only, Claude reads and writes, other models not supported. Prompt caching: OpenAI implicit for prompts of 1,024+ tokens; Claude explicit via cache_control (ephemeral only, 5-minute TTL, max 4 breakpoints); cache_control and prompt_cache_key ignored for non-supporting families — probes: `cached-tokens.cached-tokens`; expect: adjustment-needed (official-page)
  - reasoning_effort: supported for OpenAI models (values none, minimal, low, medium, high), ignored for Claude and other models; reasoning.effort overrides reasoning_effort for OpenAI, is converted to reasoning.max_tokens for Claude, ignored for other; reasoning.max_tokens ignored for OpenAI, supported for Claude — probes: `reasoning.reasoning-effort`; expect: adjustment-needed; silent (official-page)
  - Reasoning output: only Claude models return reasoning details (choices[].message.reasoning and delta.reasoning in OpenRouter format, messages[].reasoning_details input); usage.completion_tokens_details.reasoning_tokens supported for OpenAI models only; OpenAI models do not return reasoning text — probes: `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - temperature is ignored for Claude Opus 4.7 (model no longer supports it); temperature and top_p supported for all families — probes: `ranges.temperature-2`; expect: compliant (official-page)
  - n supported for OpenAI models, ignored for Claude and other models (choices[].index single choice only for those) — probes: `ranges.n-2`; expect: unsupported; silent (official-page)
  - logprobs and top_logprobs supported for OpenAI models, ignored for Claude and other models; choices[].logprobs not supported in their responses — probes: `ranges.logprobs`; expect: unsupported; silent (official-page)
  - stop supported for OpenAI models, ignored for Claude and other models — probes: `ranges.stop-5`; expect: unsupported; silent (official-page)
  - frequency_penalty, presence_penalty, logit_bias, prediction, verbosity supported for OpenAI models only (ignored for others); prompt_cache_key OpenAI only — probes: no probe; expect: unsupported; silent (official-page)
  - service_tier and store return errors for all model families; metadata, modalities, safety_identifier ignored for all families; web_search_options errors for OpenAI models, ignored for others — probes: no probe; expect: unsupported (official-page)
  - Response fields: choices[].finish_reason not supported for Claude, only 'stop' for other models; delta.role, refusal, function_call not supported for Claude/other; system_fingerprint and service_tier in the response only for OpenAI models — probes: no probe; expect: adjustment-needed (official-page)
  - Response headers openai-organization, openai-version, openai-processing-ms, x-ratelimit-limit/remaining/reset-requests and -tokens, and retry-after are all documented as not supported; rate limits are per account and per model, 429 'too many requests' on exceeding RPM/TPM — probes: `errors.rate-limit-headers`; expect: unsupported (official-page)
  - Errors: statuses are 400 (invalid options object, unknown model, schema validation failed, max tokens exceeded, all requests throttled by remote service), 402 budget exceeded, 403 not authorized, 429 too many requests, 503 inference timed out; error messages are generated by Snowflake, not by the model provider. Unknown model returns 400, not 404 — probes: `errors.error-shape`; expect: adjustment-needed (official-page)
  - seed is not listed in the compatibility chart at all (no support or ignore statement); gap, not a documented limit — probes: `ranges.seed`; expect: adjustment-needed (official-page)
- Docs read:
  - https://docs.snowflake.com/en/user-guide/snowflake-cortex/cortex-rest-api — fetched, 2026-10-20
  - https://docs.snowflake.com/en/user-guide/snowflake-cortex/open_ai_sdk — fetched, 2026-10-20
- Notes: The old /open_ai_sdk URL now serves the same 'Cortex REST API' page (canonical .../cortex-rest-api). Page offers two APIs sharing authentication, model catalog and rate limits: Chat Completions (POST /api/v2/cortex/v1/chat/completions, all models, OpenAI SDKs) and Messages (POST /api/v2/cortex/v1/messages, Claude only, Anthropic SDK). seed is absent from the request-field chart, so its behavior is unknown (the last deviation entry records that gap as adjustment-needed; it is not a documented limit). The docs do not mention a Responses endpoint or a models-list endpoint. MODEL IDENTITY: the response `model` field is documented as supported for all families ('model: Supported' in the response-field chart) but the docs do NOT state that it names the model that answered or in what form; model names are the Snowflake catalog names (e.g. openai-gpt-5); needs live probe. Usage is monitored via CORTEX_REST_API_USAGE_HISTORY, not AI_OBSERVABILITY_EVENTS. Requires a Snowflake account with credits, so no free-tier CI. Throttle: per-account per-model sliding-window RPM/TPM; 429 responses should be retried with exponential backoff.

## Priority 3 · gateway

### Cloudflare AI Gateway (`cloudflare-gateway`)

- Tier / hosting: Priority 3 / gateway
- Secret: `CLOUDFLARE_GATEWAY_API_KEY`
- Base URL: `https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/ai/v1` (official-page)
- Auth: bearer via `Authorization` — REST API: Authorization: Bearer <Cloudflare API token> with Account > Workers AI > Read permission (an AI Gateway-only token returns 401 error code 10000). Third-party models are billed via Unified Billing credits; optional cf-aig-gateway-id header selects a gateway (default gateway otherwise). Legacy compat endpoint: provider key as Bearer (or stored BYOK with a Cloudflare token as apiKey), plus cf-aig-authorization: Bearer <cf token> if the gateway is authenticated.
- Models: chat `openai/gpt-4.1-nano`, reasoning `openai/gpt-5-nano` (official-page) — Naming: {author}/{model} for third-party models (openai/gpt-4.1, anthropic/claude-sonnet-4, google/gemini-3-flash, xai/grok-3) and @cf/author/model for Workers AI (needs cf-aig-gateway-id); dynamic/{route} for dynamic routes. Legacy compat page also uses {provider}/{model} with provider names such as google-ai-studio, grok, workers-ai (e.g. google-ai-studio/gemini-2.5-flash, grok/grok-4, workers-ai/@cf/meta/llama-3.3-70b-instruct-fp8-fast), a different provider vocabulary from the REST API's author names. Ids and prices read on developers.cloudflare.com/ai/models/: openai/gpt-4.1-nano $0.10/$0.40 (non-reasoning), openai/gpt-5-nano $0.05/$0.40 (reasoning tokens), openai/gpt-5-mini $0.25/$2.00, google/gemini-2.5-flash-lite $0.10/$0.40. Workers AI (@cf) models were not priced. Round 1 compat example id openai/gpt-5-mini is still on the compat page parameter list but is not the cheapest.
- Price (chat model, USD per million tokens): input 0.1, output 0.4 (official-page (developers.cloudflare.com/ai/models/openai/gpt-4.1-nano). openai/gpt-5-nano: 0.05 / 0.40. Unified Billing passes provider rates through with a 5% fee on credit purchases)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1/messages` (official-page (ai-gateway/usage/rest-api: POST /ai/v1/messages Anthropic format; supports Anthropic and other third-party models, not Workers AI @cf models))
- Verification: **partial**
- Documented deviations:
  - The OpenAI-compatible endpoint https://gateway.ai.cloudflare.com/v1/{account_id}/{gateway_id}/compat/chat/completions is deprecated for standard single-model calls; it still works for existing integrations and for dynamic/{route} models. New integrations are pointed to the REST API at api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/v1/chat/completions — probes: no probe; expect: compliant (official-page)
  - REST API /ai/v1/chat/completions is described as using the standard OpenAI chat completions format and OpenAI SDK compatible; the page lists no parameter table and no unsupported or ignored parameters, so per-parameter behavior depends on the upstream model/provider — probes: no probe; expect: compliant (official-page)
  - /ai/v1/responses supports third-party models and Workers AI models only where the model supports the Responses API (e.g. GPT-OSS); Workers AI @cf models do not support /ai/v1/messages — probes: `responses.responses-basic`; expect: adjustment-needed (official-page)
  - cf-aig-* request headers (cache, retry, timeout, metadata) are Cloudflare extensions; authenticated gateways on the compat endpoint need cf-aig-authorization — probes: no probe; expect: compliant (official-page)
- Docs read:
  - https://developers.cloudflare.com/ai-gateway/usage/chat-completion/ — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai-gateway/usage/rest-api/ — fetched, 2026-10-20
  - https://developers.cloudflare.com/changelog/post/2026-05-21-rest-api/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai-gateway/get-started/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai-gateway/features/unified-billing/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai-gateway/llms.txt — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai/models/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai/models/openai/gpt-5-nano/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai/models/openai/gpt-4.1-nano/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai/models/openai/gpt-5-mini/index.md — fetched, 2026-10-20
  - https://developers.cloudflare.com/ai/models/google/gemini-2.5-flash-lite/index.md — fetched, 2026-10-20
- Notes: Gateway; neither the compat page nor the REST API page contains a parameter table, so OpenAI-compat verification is partial. Default REST base URL changed: since 2026-05-21 the primary surface is api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/v1 with four routes (POST /ai/run, /ai/v1/chat/completions, /ai/v1/responses, /ai/v1/messages); the old compat base https://gateway.ai.cloudflare.com/v1/{account_id}/{gateway_id}/compat (page example uses gateway id 'default', created on first request) remains for existing integrations. MODEL IDENTITY: requests name the model {author}/{model}; the docs do NOT document what the response `model` field contains (no response example or statement was found on the pages read; the only 'model' echo is in the /ai/run webhook payload: "model": "google/nano-banana" alongside "provider": "google"). Needs live probe. Unified Billing requires loaded credits before third-party calls; Workers AI models can use prepaid credits or Workers AI billing. Requests to frontier models billed with prepaid credits get higher rate limits (text not fully read). Rate-limit headers and error shape not documented on these pages.

### Vercel AI Gateway (`vercel`)

- Tier / hosting: Priority 3 / gateway
- Secret: `VERCEL_API_KEY`
- Base URL: `https://ai-gateway.vercel.sh/v1` (official-page)
- Auth: bearer via `Authorization` — Authorization: Bearer <AI Gateway API key or Vercel OIDC token>; if an API key is specified it takes precedence over any OIDC token even if the API key is invalid. Docs' own env var is AI_GATEWAY_API_KEY.
- Models: chat `meta/llama-3.1-8b`, reasoning `openai/gpt-oss-20b` (official-page) — Model naming: creator/model-name (e.g. openai/gpt-6-astra, anthropic/claude-opus-5, spacexai/grok-4.5, alibaba/qwen3.7-flash). From the official listing vercel.com/ai-gateway/models (412 chat/image/video/embedding models; GET https://ai-gateway.vercel.sh/v1/models returns 477 ids without auth): meta/llama-3.1-8b chat, $0.02/$0.05, tool-use, providers bedrock+novita, 128K; openai/gpt-oss-20b $0.07/$0.30, reasoning, tool-use, structured-output, 131.1K. Cheaper listed options: mistral/mistral-nemo ($0.03/$0.03), alibaba/qwen3.7-flash ($0.03/$0.13, reasoning), inclusionai/ling-3.0-flash ($0.02/$0.06, reasoning, provider novita), and four $0 ids (nvidia/nemotron-3.5-lightning-free, poolside/laguna-s-2.1-free, stealth/glyph-cluster, minimax/minimax-m3.1-flash listed at $0.00) that may be rate-limited or time-boxed. Routed providers vary by model.
- Price (chat model, USD per million tokens): input 0.02, output 0.05 (official-page (vercel.com/ai-gateway/models listing, meta/llama-3.1-8b). openai/gpt-oss-20b: 0.07 / 0.30)
- Endpoints: chat completions True, responses True
- Anthropic Messages endpoint (later phase): offered True, `https://ai-gateway.vercel.sh/v1/messages` (official-page (anthropic-messages-api page: base URL https://ai-gateway.vercel.sh, POST /v1/messages, /v1/messages/count_tokens, batches))
- Verification: **verified**
- Documented deviations:
  - Chat Completions parameter list: stream, temperature (0-2), max_tokens, top_p, frequency_penalty, presence_penalty, stop, safety_identifier, tools, tool_choice, providerOptions, provider, response_format. max_completion_tokens is not listed (max_tokens is) — probes: `output-cap.max-tokens`, `output-cap.max-completion-tokens`; expect: adjustment-needed (official-page)
  - Not listed among supported Chat Completions parameters: n, seed, logprobs/top_logprobs, stream_options, parallel_tool_calls, store, metadata — probes: `ranges.n-2`, `ranges.seed`, `ranges.logprobs`, `streaming-usage.usage-included`, `streaming-usage.usage-default`; expect: adjustment-needed (official-page)
  - tool_choice documented as auto, none, or a specific function; required is not listed on the Chat Completions page (the Responses page lists auto, required, none, specific function) — probes: `tools.tool-required`; expect: adjustment-needed (official-page)
  - response_format: OpenAI json_schema {name, schema, strict?, description?}, legacy {type: json, schema?, name?, description?}, and text are documented; json_object is not listed for Chat Completions (the Responses text option lists json_schema and json_object) — probes: `structured-output.json-schema-strict`, `structured-output.json-object`; expect: adjustment-needed (official-page)
  - Reasoning uses a Gateway extension: request reasoning object {enabled, effort, max_tokens, exclude}; effort and max_tokens cannot be combined; effort accepts none, minimal, low, medium, high, xhigh, max subject to the model's catalog entry; top-level reasoning_effort is an alias and the nested value wins; max can map to xhigh on cross-provider routes; legacy reasoning.max_tokens is rejected by newer adaptive-only Claude models — probes: `reasoning.reasoning-effort`; expect: adjustment-needed (official-page)
  - Reasoning text is returned in a non-standard message.reasoning field and streamed in delta.reasoning; usage.completion_tokens_details.reasoning_tokens is documented; with exclude true reasoning content is omitted (internal reasoning still runs). Reasoning text may be omitted even when thinking runs — probes: `reasoning.reasoning-shape`, `reasoning-replay.reasoning-replay`; expect: adjustment-needed (official-page)
  - Gateway extensions not in the OpenAI schema: models (fallback list), providerOptions.gateway (order/only/sort/caching/safetyIdentifier), provider.sort, caching/cache_control, reasoning — probes: no probe; expect: compliant (official-page)
  - Content parts: text and image_url (data URL or URL) documented; PDFs and other files go as type file with data, media_type and filename — probes: `content-shape.content-parts`, `images.image-part`; expect: compliant (official-page)
  - Responses API (POST /v1/responses, same base URL) documents store, previous_response_id, max_output_tokens, reasoning.effort (none..xhigh), text json_schema/json_object, truncation, caching; no limits on store are stated — probes: `responses.responses-basic`, `responses.responses-store`; expect: compliant (official-page)
  - Common error statuses documented: 400, 401, 403, 404, 429, 500; error body shape and rate-limit headers not documented — probes: `errors.error-shape`, `errors.rate-limit-headers`; expect: adjustment-needed (official-page)
- Docs read:
  - https://vercel.com/docs/ai-gateway/openai-compat — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/chat-completions — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/streaming — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/reasoning — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/structured-outputs — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/tool-calling — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/images — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/advanced — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/openai-chat-completions/rest-api — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/responses — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/sdks-and-apis/anthropic-messages-api — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/models-and-providers — fetched, 2026-10-20
  - https://vercel.com/ai-gateway/models — fetched, 2026-10-20
  - https://vercel.com/docs/ai-gateway/models-and-providers/reasoning — fetched, 2026-10-20
  - https://ai-gateway.vercel.sh/v1/models — not-reached, 2026-10-20
- Notes: Gateway; endpoints: GET /models, GET /models/{model}, POST /chat/completions, POST /embeddings (same base URL also serves Responses, Messages and an OpenAI-compatible Decisions API). MODEL IDENTITY: requests name the model as creator/model-name (e.g. anthropic/claude-opus-5). Every response example (non-streaming, streaming chunks, reasoning) shows the same creator/model string in the response `model` field, and the model-fallbacks example comments '// Check which model was actually used' then prints completion.model, i.e. the docs imply response `model` names the model that answered (relevant with the models fallback list), in the provider/model form; there is no explicit prose guarantee, so confirm with a live probe. The docs do not say whether the response also exposes the upstream provider. Docs' Tailscale/third-party note about omitting /v1 from round 1 is client-specific and was not re-checked. Free credits and rate limits were not read.
