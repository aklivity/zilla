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
import random
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CHAT_MODEL = "mock-chat"
REASONING_MODEL = "mock-reason"
DEFAULTS = {
    "reject_max_completion_tokens": False, "ignore_cap": False, "reject_max_tokens": False,
    "reject_parts": False, "reject_developer": False, "ignore_developer": False, "reject_two_system": False,
    "reject_consecutive": False, "reject_name": False, "reject_parallel": False, "tool_single_chunk": False,
    "reject_json_schema": False, "reject_n": False, "ignore_n": False, "ignore_logprobs": False,
    "ignore_seed": False, "stop_limit": None, "temp_max": None, "reasoning": "tokens", "reject_effort": False,
    "ignore_effort": False, "usage": "final", "usage_default": False, "reject_stream_options": False,
    "cached": "standard", "error_shape": "openai", "model_echo": "same", "responses": True, "store_false": False,
    "rate_headers": "standard", "reject_replay": False, "require_replay": False, "no_images": False,
}


def words(text):
    return text.split()


def text_of(content):
    return " ".join(p.get("text", "") for p in content if isinstance(p, dict)) if isinstance(content, list) else str(content or "")


class State:
    def __init__(self, profile):
        self.profile = {**DEFAULTS, **profile}
        self.seen = set()
        self.requests = []


def reply(handler, status, body, headers=None):
    raw = json.dumps(body).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(raw)))
    for k, v in (headers or {}).items():
        handler.send_header(k, v)
    handler.end_headers()
    handler.wfile.write(raw)


def error(state, handler, status, message):
    shape = state.profile["error_shape"]
    body = {"error": {"message": message, "type": "invalid_request_error"}} if shape == "openai" else {"detail": message}
    reply(handler, status, body)


def generate(state, req, model):
    p = state.profile
    messages = req.get("messages", [])
    joined = " ".join(text_of(m.get("content")) for m in messages)
    systems = " ".join(text_of(m["content"]) for m in messages if m["role"] in ("system", "developer"))
    reasoning, text = "", "Hello!"
    if "Count from 1 to 40" in joined:
        text = "\n".join(str(i) for i in range(1, 41))
    elif "Count from 1 to 12" in joined:
        text = ", ".join(str(i) for i in range(1, 13))
        for stop in req.get("stop") or []:
            if stop in text:
                text = text[: text.index(stop)]
    elif "PINEAPPLE" in joined:
        text = "PINEAPPLE"
    elif "BANANA" in systems and not p["ignore_developer"]:
        text = "BANANA"
    elif "DONE" in systems:
        text = "HELLO DONE"
    elif "name is Ada" in joined and "What is my name" in joined:
        text = "Ada"
    elif "dominant colour" in joined:
        text = "Red" if not p["no_images"] else "Blue"
    elif "JSON object" in joined:
        text = '{"name":"Ada","age":36}'
    elif "fox" in joined:
        text = "The fox " + ("jumps" if p["ignore_seed"] and random.random() < 0.5 else "runs") + " quickly."
    elif "Reference list" in joined:
        text = "ok"
    elif "17 times 24" in joined or "prime" in joined:
        text = "408" if "17" in joined else "62"
        effort = req.get("reasoning_effort")
        budget = 400 if effort == "high" else 40 if effort == "low" else 100
        if p["ignore_effort"]:
            budget = 100
        reasoning = " ".join(["step"] * (budget if "prime" in joined else 30))
    return text, reasoning


def usage_for(state, req, text, reasoning, cached):
    prompt = max(1, sum(len(words(text_of(m.get("content")))) for m in req.get("messages", [])))
    completion = len(words(text)) + len(words(reasoning)) if state.profile["reasoning"] != "none" else len(words(text))
    usage = {"prompt_tokens": prompt, "completion_tokens": completion, "total_tokens": prompt + completion}
    if state.profile["cached"] == "standard":
        usage["prompt_tokens_details"] = {"cached_tokens": prompt - 8 if cached else 0}
    elif state.profile["cached"] == "vendor" and cached:
        usage["prompt_cache_hit_tokens"] = prompt - 8
    if reasoning and state.profile["reasoning"] in ("tokens", "reasoning_content", "reasoning"):
        usage["completion_tokens_details"] = {"reasoning_tokens": len(words(reasoning))}
    return usage


def chat(state, handler, req):
    p = state.profile
    messages = req.get("messages", [])
    model = req.get("model")
    if model not in (CHAT_MODEL, REASONING_MODEL):
        return error(state, handler, 404, f"The model `{model}` does not exist")
    roles = [m["role"] for m in messages]
    checks = [
        ("max_completion_tokens" in req and p["reject_max_completion_tokens"], "Unsupported parameter: max_completion_tokens"),
        ("max_tokens" in req and p["reject_max_tokens"], "Unsupported parameter: max_tokens"),
        (any(isinstance(m.get("content"), list) and m["role"] == "user" and
             all(x.get("type") == "text" for x in m["content"]) for m in messages) and p["reject_parts"], "content must be a string"),
        ("developer" in roles and p["reject_developer"], "Unsupported role: developer"),
        (roles.count("system") > 1 and p["reject_two_system"], "Only one system message allowed"),
        (any(a == b == "user" for a, b in zip(roles, roles[1:])) and p["reject_consecutive"], "Roles must alternate"),
        (any("name" in m for m in messages) and p["reject_name"], "messages.name is not supported"),
        ("parallel_tool_calls" in req and p["reject_parallel"], "parallel_tool_calls is not supported"),
        (req.get("response_format", {}).get("type") == "json_schema" and p["reject_json_schema"], "json_schema is not supported"),
        (req.get("n", 1) > 1 and p["reject_n"], "n must be 1"),
        (p["temp_max"] is not None and req.get("temperature", 0) > p["temp_max"], "temperature out of range"),
        (p["stop_limit"] is not None and len(req.get("stop") or []) > p["stop_limit"], "too many stop sequences"),
        ("reasoning_effort" in req and p["reject_effort"], "reasoning_effort is not supported"),
        ("stream_options" in req and p["reject_stream_options"], "stream_options is not supported"),
        (any("image_url" in str(m.get("content")) for m in messages) and p["no_images"] and p["reject_parts"],
         "images are not supported"),
        (any("reasoning_content" in m or "reasoning" in m for m in messages) and p["reject_replay"], "reasoning is not accepted in input"),
    ]
    for failed, message in checks:
        if failed:
            return error(state, handler, 400, message)
    prompt_key = json.dumps(messages, sort_keys=True)
    cached = prompt_key in state.seen
    state.seen.add(prompt_key)
    text, reasoning = generate(state, req, model)
    cap = None if p["ignore_cap"] else (req.get("max_completion_tokens") or req.get("max_tokens"))
    finish = "stop"
    if cap and len(words(text)) > cap:
        text, finish = " ".join(words(text)[:cap]), "length"
    if req.get("tools") and req.get("tool_choice") in ("required", "auto"):
        return tool_reply(state, handler, req, text, finish)
    usage = usage_for(state, req, text, reasoning, cached)
    returned = model if p["model_echo"] == "same" else "vendor/" + model + "-2026"
    if req.get("stream"):
        return stream(state, handler, req, returned, text, reasoning, usage)
    message = {"role": "assistant", "content": text, "refusal": None, "annotations": []}
    if reasoning and p["reasoning"] in ("reasoning_content", "reasoning"):
        message[p["reasoning"]] = reasoning
    count = 1 if p["ignore_n"] else req.get("n", 1)
    choices = [{"index": i, "message": dict(message), "finish_reason": finish} for i in range(count)]
    if req.get("logprobs") and not p["ignore_logprobs"]:
        for c in choices:
            c["logprobs"] = {"content": [{"token": "x", "logprob": -0.1, "top_logprobs": []}]}
    headers = {"x-ratelimit-remaining-requests": "99"} if p["rate_headers"] == "standard" else (
        {"x-vendor-ratelimit-remaining": "99"} if p["rate_headers"] == "vendor" else {})
    reply(handler, 200, {"id": "chatcmpl-1", "object": "chat.completion", "created": 1, "model": returned,
                         "service_tier": "default", "choices": choices, "usage": usage}, headers)


def tool_reply(state, handler, req, text, finish):
    calls = [{"id": "call_1", "type": "function", "function": {"name": "get_weather", "arguments": '{"city":"Paris"}'}}]
    if req.get("stream"):
        sse_open(handler)
        fragments = ['{"city":"Paris"}'] if state.profile["tool_single_chunk"] else ['{"ci', 'ty":"Pa', 'ris"}']
        first = {"index": 0, "id": "call_1", "type": "function", "function": {"name": "get_weather", "arguments": ""}}
        deltas = [first] + [{"index": 0, "function": {"arguments": f}} for f in fragments]
        for d in deltas:
            sse(handler, {"id": "c", "object": "chat.completion.chunk", "model": CHAT_MODEL,
                          "choices": [{"index": 0, "delta": {"tool_calls": [d]}, "finish_reason": None}]})
        sse(handler, {"id": "c", "object": "chat.completion.chunk", "model": CHAT_MODEL,
                      "choices": [{"index": 0, "delta": {}, "finish_reason": "tool_calls"}]})
        sse_close(handler)
        return None
    return reply(handler, 200, {"id": "c", "object": "chat.completion", "created": 1, "model": CHAT_MODEL, "choices": [
        {"index": 0, "message": {"role": "assistant", "content": None, "tool_calls": calls, "refusal": None, "annotations": []}, "finish_reason": "tool_calls"}],
        "usage": {"prompt_tokens": 10, "completion_tokens": 8, "total_tokens": 18}})


def write_chunk(handler, raw):
    handler.wfile.write(f"{len(raw):x}\r\n".encode() + raw + b"\r\n")
    handler.wfile.flush()


def sse(handler, obj):
    write_chunk(handler, b"data: " + json.dumps(obj).encode() + b"\n\n")


def sse_open(handler):
    handler.send_response(200)
    handler.send_header("Content-Type", "text/event-stream")
    handler.send_header("Transfer-Encoding", "chunked")
    handler.end_headers()


def sse_close(handler):
    write_chunk(handler, b"data: [DONE]\n\n")
    handler.wfile.write(b"0\r\n\r\n")
    handler.wfile.flush()


def stream(state, handler, req, model, text, reasoning, usage):
    p = state.profile
    sse_open(handler)
    include = (req.get("stream_options") or {}).get("include_usage") or p["usage_default"]
    parts = words(text) or [""]
    for i, w in enumerate(parts):
        sse(handler, {"id": "c", "object": "chat.completion.chunk", "model": model,
                      "choices": [{"index": 0, "delta": {"content": w + " "}, "finish_reason": None}]})
    last = {"id": "c", "object": "chat.completion.chunk", "model": model,
            "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]}
    if include and p["usage"] == "content":
        last["usage"] = usage
    if include and p["usage"] == "vendor":
        last["x_vendor"] = {"usage": usage}
    sse(handler, last)
    if include and p["usage"] == "final":
        sse(handler, {"id": "c", "object": "chat.completion.chunk", "model": model, "choices": [], "usage": usage})
    sse_close(handler)


def responses(state, handler, req):
    if not state.profile["responses"]:
        return error(state, handler, 404, "Not found")
    return reply(handler, 200, {"id": "resp_1", "object": "response", "model": req.get("model"),
                                "store": False if state.profile["store_false"] else req.get("store", True),
                                "output": [{"type": "message", "content": [{"type": "output_text", "text": "hello"}]}],
                                "usage": {"input_tokens": 5, "output_tokens": 2}})


def make_handler(state):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args):
            return None

        def body(self):
            if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
                raw = b""
                while True:
                    size = int(self.rfile.readline().strip() or b"0", 16)
                    if size == 0:
                        self.rfile.readline()
                        break
                    raw += self.rfile.read(size)
                    self.rfile.readline()
            else:
                raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            return json.loads(raw or b"{}")

        def do_POST(self):
            req = self.body()
            state.requests.append((self.path, dict(self.headers), req))
            if self.path.endswith("/chat/completions"):
                chat(state, self, req)
            elif self.path.endswith("/responses"):
                responses(state, self, req)
            else:
                error(state, self, 404, "Not found")

    return Handler


class MockProvider:
    def __init__(self, profile=None, port=0):
        self.state = State(profile or {})
        self.server = ThreadingHTTPServer(("127.0.0.1", port), make_handler(self.state))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    @property
    def base_url(self):
        return f"http://127.0.0.1:{self.server.server_address[1]}/v1"

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *exc):
        self.server.shutdown()
        self.server.server_close()


if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    provider = MockProvider(json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}, port)
    print(provider.base_url, flush=True)
    provider.server.serve_forever()
