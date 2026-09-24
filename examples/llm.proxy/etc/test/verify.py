#!/usr/bin/env python3
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

# Assertions, all driven through the *official* openai / anthropic Python
# SDKs rather than hand-built HTTP calls -- proving the dialects this example
# exercises are wire-compatible enough for an off-the-shelf client to
# succeed against them unmodified, not just compatible with our own
# hand-rolled request/response shapes.
#
#   1. an OpenAI SDK client talking to the openai-facing frontend
#      (north_llm_server_openai, port 7161) gets back a response the SDK
#      itself parses as OpenAI-shaped, even though the real upstream
#      (mock-anthropic) only speaks the Anthropic dialect -- proving
#      request+response cross-dialect translation, openai in -> anthropic
#      upstream -> openai out, in both non-streaming and streaming form
#   2. the same round trip, but the request carries a `tools` definition,
#      and the response carries an OpenAI-shaped tool call translated from
#      the upstream's native Anthropic tool-use content block
#   3. an Anthropic SDK client talking to the anthropic-facing frontend
#      (north_llm_server_anthropic, port 7162) gets back a response the SDK
#      parses as Anthropic-shaped, even though the real upstream
#      (mock-openai) only speaks the OpenAI dialect -- the same translation,
#      mirrored, non-streaming and streaming
#   4. the same round trip, but with `tools`, translating the upstream's
#      native OpenAI tool call into an Anthropic-shaped tool-use block
#   5. south_llm_client_anthropic forwards the caller's own bearer token
#      upstream as `x-api-key` (options.authorization pass-through)
#   6. south_llm_client_openai forwards the caller's own `x-api-key` upstream
#      as `Authorization: Bearer ...`, the same pass-through mirrored
#   7. north_llm_proxy routes a gpt-4o request from the openai-facing
#      frontend to mock-openai-secondary (same dialect, no translation)
#      instead of the default cross-dialect route to mock-anthropic --
#      routing purely on LlmBeginEx.model, never on request content --
#      exercised both non-streaming and streaming
#   8. north_llm_proxy routes a claude-3-5-haiku-20241022 request from the
#      anthropic-facing frontend to mock-anthropic-secondary, the same
#      model-based intra-dialect routing mirrored, non-streaming and
#      streaming
#   9. every llm binding an exchange passes through -- server, proxy and
#      client -- records that exchange's llm.* metrics, scraped from the
#      prometheus exporter: the upstream's reported token usage, a token
#      count the upstream dialect never reports left unrecorded, one
#      duration sample, and no request left active

import os
import re
import socket
import subprocess
import sys
import time
import urllib.request

from anthropic import Anthropic
from openai import OpenAI

FAIL_FAST = os.environ.get("FAIL_FAST", "0") == "1"
GITHUB_ACTIONS = os.environ.get("GITHUB_ACTIONS")

OPENAI_BASE_URL = "http://zilla:7161/v1"
ANTHROPIC_BASE_URL = "http://zilla:7162"
METRICS_URL = "http://zilla:7190/metrics"

failures = []
timings = []
exit_code = 0


def fail(message):
    global exit_code
    print(f"❌ {message}")
    failures.append(message)
    exit_code = 1
    if FAIL_FAST:
        report_failures()
        sys.exit(1)


def report_failures():
    if failures:
        print("=== failed assertions ===")
        for failure in failures:
            print(f"❌ {failure}")
            if GITHUB_ACTIONS:
                print(f"::error::{failure}")


def report_timings():
    if timings:
        print("=== elapsed by assertion ===")
        total = 0
        for label, elapsed in timings:
            total += elapsed
            print(f"{elapsed:5d}s  {label}")
        print(f"{total:5d}s  total")


def run_check(label, check, attempts=10, delay=1):
    start = time.time()
    last_exc = None
    for attempt in range(1, attempts + 1):
        try:
            check()
            timings.append((label, int(time.time() - start)))
            print(f"✅ {label}")
            return
        except Exception as exc:  # noqa: BLE001 -- retried until the final attempt
            last_exc = exc
            if attempt < attempts:
                if attempt > 1:
                    time.sleep(delay)
                continue
    timings.append((label, int(time.time() - start)))
    fail(f"{label}: {last_exc}")


def openai_client(api_key="test-openai-key"):
    return OpenAI(base_url=OPENAI_BASE_URL, api_key=api_key, timeout=10.0)


def anthropic_client(api_key="test-anthropic-key"):
    return Anthropic(base_url=ANTHROPIC_BASE_URL, api_key=api_key, timeout=10.0)


def openai_chat(model, streaming, tools=None):
    client = openai_client()
    kwargs = {"model": model, "messages": [{"role": "user", "content": "Hello"}]}
    if tools is not None:
        kwargs["messages"] = [{"role": "user", "content": "What is the weather in Paris?"}]
        kwargs["tools"] = tools

    if not streaming:
        return client.chat.completions.create(**kwargs)

    content = ""
    finish_reason = None
    for chunk in client.chat.completions.create(**kwargs, stream=True):
        # The final chunk carrying usage (mirroring stream_options.include_usage)
        # has no choices at all -- only chunks with a choice carry a delta/finish_reason.
        if not chunk.choices:
            continue
        delta = chunk.choices[0].delta
        if delta.content:
            content += delta.content
        if chunk.choices[0].finish_reason:
            finish_reason = chunk.choices[0].finish_reason
    return content, finish_reason


def anthropic_message(model, streaming, tools=None):
    client = anthropic_client()
    kwargs = {"model": model, "max_tokens": 1024, "messages": [{"role": "user", "content": "Hello"}]}
    if tools is not None:
        kwargs["messages"] = [{"role": "user", "content": "What is the weather in Paris?"}]
        kwargs["tools"] = tools

    if not streaming:
        return client.messages.create(**kwargs)

    text = ""
    stop_reason = None
    with client.messages.stream(**kwargs) as stream:
        for event in stream:
            if event.type == "content_block_delta" and event.delta.type == "text_delta":
                text += event.delta.text
            elif event.type == "message_delta":
                stop_reason = event.delta.stop_reason
    return text, stop_reason


# WHEN: an OpenAI SDK client sends a plain chat request to the openai-facing
#       frontend, backed by the Anthropic-only mock-anthropic
# THEN: the SDK parses back an OpenAI-shaped response, proving the
#       anthropic-native reply was translated back to openai on the way out
def check_openai_text_default():
    resp = openai_chat("gpt-4", streaming=False)
    assert resp.choices[0].message.content == "Hello! How can I help you today?", resp
    assert resp.choices[0].finish_reason == "stop", resp


def check_openai_text_default_streaming():
    content, finish_reason = openai_chat("gpt-4", streaming=True)
    assert content == "Hello! How can I help you today?", content
    assert finish_reason == "stop", finish_reason


# WHEN: the same client instead sends a request carrying a `tools` definition
# THEN: the SDK parses an openai-shaped tool call, translated from
#       mock-anthropic's native tool_use content block
def check_openai_tools_default():
    tools = [{
        "type": "function",
        "function": {"name": "get_weather", "parameters": {"type": "object", "properties": {"city": {"type": "string"}}}}
    }]
    resp = openai_chat("gpt-4", streaming=False, tools=tools)
    assert resp.choices[0].finish_reason == "tool_calls", resp
    assert resp.choices[0].message.tool_calls[0].function.name == "get_weather", resp


# WHEN: an OpenAI SDK client sends a request naming model gpt-4o
# THEN: north_llm_proxy routes it to mock-openai-secondary, not the default
#       cross-dialect route to mock-anthropic -- routing purely on
#       LlmBeginEx.model, never on request content
def check_openai_text_secondary():
    resp = openai_chat("gpt-4o", streaming=False)
    assert "secondary openai-dialect deployment" in resp.choices[0].message.content, resp


def check_openai_text_secondary_streaming():
    content, finish_reason = openai_chat("gpt-4o", streaming=True)
    assert "secondary openai-dialect deployment" in content, content
    assert finish_reason == "stop", finish_reason


# WHEN: an Anthropic SDK client sends a plain messages request to the
#       anthropic-facing frontend, backed by the OpenAI-only mock-openai
# THEN: the SDK parses back an Anthropic-shaped response, proving the
#       openai-native reply was translated back to anthropic on the way out
def check_anthropic_text_default():
    resp = anthropic_message("claude-3-opus-20240229", streaming=False)
    assert resp.content[0].text == "Hello! How can I help you today?", resp
    assert resp.stop_reason == "end_turn", resp


def check_anthropic_text_default_streaming():
    text, stop_reason = anthropic_message("claude-3-opus-20240229", streaming=True)
    assert text == "Hello! How can I help you today?", text
    assert stop_reason == "end_turn", stop_reason


# WHEN: the same client instead sends a request carrying a `tools` definition
# THEN: the SDK parses an anthropic-shaped tool_use block, translated from
#       mock-openai's native tool_calls entry
def check_anthropic_tools_default():
    tools = [{"name": "get_weather", "input_schema": {"type": "object", "properties": {"city": {"type": "string"}}}}]
    resp = anthropic_message("claude-3-opus-20240229", streaming=False, tools=tools)
    assert resp.stop_reason == "tool_use", resp
    assert resp.content[0].type == "tool_use", resp
    assert resp.content[0].name == "get_weather", resp


# WHEN: an Anthropic SDK client sends a request naming model
#       claude-3-5-haiku-20241022
# THEN: north_llm_proxy routes it to mock-anthropic-secondary, not the
#       default cross-dialect route to mock-openai
def check_anthropic_text_secondary():
    resp = anthropic_message("claude-3-5-haiku-20241022", streaming=False)
    assert "secondary anthropic-dialect deployment" in resp.content[0].text, resp


def check_anthropic_text_secondary_streaming():
    text, stop_reason = anthropic_message("claude-3-5-haiku-20241022", streaming=True)
    assert "secondary anthropic-dialect deployment" in text, text
    assert stop_reason == "end_turn", stop_reason


# Scrapes the prometheus exporter into {(sample, binding, le): value}, where
# le is the cumulative bucket limit for a histogram _bucket sample and None
# for every other sample.
def scrape_metrics():
    with urllib.request.urlopen(METRICS_URL, timeout=10) as resp:
        text = resp.read().decode()
    samples = {}
    for line in text.splitlines():
        match = re.match(r'^(llm_[a-z_]+)\{([^}]*)\} (\S+)$', line)
        if match:
            binding = re.search(r'binding="([^"]+)"', match.group(2)).group(1)
            le = re.search(r'le="([^"]+)"', match.group(2))
            samples[(match.group(1), binding, le.group(1) if le else None)] = float(match.group(3))
    return samples


# Histogram sums are reported from bucket limits, not exact values, so a
# recorded value is asserted by the power-of-two bucket it lands in: the
# cumulative bucket at limit grows by one, the one below it does not.
def assert_llm_metrics(exchange, bindings):
    before = scrape_metrics()
    exchange()
    after = scrape_metrics()

    def delta(sample, binding, le=None):
        return after.get((sample, binding, le), 0) - before.get((sample, binding, le), 0)

    for binding in bindings:
        assert delta("llm_tokens_input_count", binding) == 1, (binding, "input count")
        assert delta("llm_tokens_input_bucket", binding, "32") == 1, (binding, "input 25 < 32")
        assert delta("llm_tokens_input_bucket", binding, "16") == 0, (binding, "input 25 >= 16")
        assert delta("llm_tokens_output_count", binding) == 1, (binding, "output count")
        assert delta("llm_tokens_output_bucket", binding, "16") == 1, (binding, "output 15 < 16")
        assert delta("llm_tokens_output_bucket", binding, "8") == 0, (binding, "output 15 >= 8")
        assert delta("llm_tokens_total_count", binding) == 0, (binding, "total not reported")
        assert delta("llm_duration_milliseconds_count", binding) == 1, (binding, "duration count")
        assert after.get(("llm_active_requests", binding, None), 0) == 0, (binding, "active requests")


# WHEN: an OpenAI SDK client sends a chat request, routed across dialects to
#       mock-anthropic, which reports input and output tokens but no total
# THEN: north_llm_server_openai, north_llm_proxy and south_llm_client_anthropic
#       each record the reported tokens and one duration sample, leave the
#       unreported total unrecorded, and have no request left active
def check_openai_llm_metrics():
    assert_llm_metrics(
        lambda: openai_chat("gpt-4", streaming=False),
        ["north_llm_server_openai", "north_llm_proxy", "south_llm_client_anthropic"])


# WHEN: an Anthropic SDK client sends a messages request, routed across
#       dialects to mock-openai, which reports prompt and completion tokens
#       but no total
# THEN: the same llm.* metrics are recorded at north_llm_server_anthropic,
#       north_llm_proxy and south_llm_client_openai
def check_anthropic_llm_metrics():
    assert_llm_metrics(
        lambda: anthropic_message("claude-3-opus-20240229", streaming=False),
        ["north_llm_server_anthropic", "north_llm_proxy", "south_llm_client_openai"])


# Reads a compose service's own docker logs, by compose project + service
# label, so the credential pass-through checks below can confirm what a mock
# backend actually received on the wire.
def compose_project():
    out = subprocess.run(
        ["docker", "inspect", "-f", '{{ index .Config.Labels "com.docker.compose.project" }}', socket.gethostname()],
        capture_output=True, text=True, check=True)
    return out.stdout.strip()


def service_logs(project, service):
    ps = subprocess.run(
        ["docker", "ps", "-aq",
         "--filter", f"label=com.docker.compose.project={project}",
         "--filter", f"label=com.docker.compose.service={service}"],
        capture_output=True, text=True, check=True)
    container_id = ps.stdout.strip()
    logs = subprocess.run(["docker", "logs", container_id], capture_output=True, text=True)
    return logs.stdout + logs.stderr


# WHEN: a caller presents its own bearer token to the openai-facing frontend
# THEN: south_llm_client_anthropic forwards that exact token upstream as
#       x-api-key (no "Bearer " prefix) -- the caller's own credential, not a
#       separate secret configured in zilla.yaml
def check_credential_pass_through_to_anthropic():
    token = f"caller-openai-token-{os.getpid()}"
    openai_client(api_key=token).chat.completions.create(
        model="gpt-4", messages=[{"role": "user", "content": "Hello"}])
    logs = service_logs(PROJECT, "mock-anthropic")
    assert f"x-api-key: {token}" in logs, logs


# WHEN: a caller presents its own x-api-key to the anthropic-facing frontend
# THEN: south_llm_client_openai forwards that exact token upstream as
#       Authorization: Bearer ... -- the caller's own credential, not a
#       separate secret configured in zilla.yaml
def check_credential_pass_through_to_openai():
    token = f"caller-anthropic-token-{os.getpid()}"
    anthropic_client(api_key=token).messages.create(
        model="claude-3-opus-20240229", max_tokens=1024, messages=[{"role": "user", "content": "Hello"}])
    logs = service_logs(PROJECT, "mock-openai")
    assert f"authorization: Bearer {token}" in logs, logs


PROJECT = compose_project()

run_check("openai_text_default", check_openai_text_default)
run_check("openai_text_default_streaming", check_openai_text_default_streaming)
run_check("openai_tools_default", check_openai_tools_default)
run_check("openai_text_secondary", check_openai_text_secondary)
run_check("openai_text_secondary_streaming", check_openai_text_secondary_streaming)
run_check("anthropic_text_default", check_anthropic_text_default)
run_check("anthropic_text_default_streaming", check_anthropic_text_default_streaming)
run_check("anthropic_tools_default", check_anthropic_tools_default)
run_check("anthropic_text_secondary", check_anthropic_text_secondary)
run_check("anthropic_text_secondary_streaming", check_anthropic_text_secondary_streaming)
run_check("credential_pass_through_to_anthropic", check_credential_pass_through_to_anthropic)
run_check("credential_pass_through_to_openai", check_credential_pass_through_to_openai)
run_check("openai_llm_metrics", check_openai_llm_metrics)
run_check("anthropic_llm_metrics", check_anthropic_llm_metrics)

report_timings()
report_failures()

sys.exit(exit_code)
