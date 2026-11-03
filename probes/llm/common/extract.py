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
import re

from . import jsonpath, schemacheck

FUNCS = {}
STANDARD_CHUNK_KEYS = {"id", "object", "created", "model", "choices", "usage", "system_fingerprint", "service_tier"}
RATE_LIMIT_NAME = re.compile(r"rate.?limit|retry-after|ratelimit", re.IGNORECASE)
FENCE = re.compile(r"^\s*```(?:json)?\s*(.*?)\s*```\s*$", re.DOTALL)


def derive(name):
    def register(fn):
        FUNCS[name] = fn
        return fn

    return register


def _message(step):
    return jsonpath.get(step, "json.choices[0].message") or {}


def _usage(step):
    usage = jsonpath.get(step, "json.usage")
    if not isinstance(usage, dict) and step.get("stream"):
        usage = next((c["usage"] for c in reversed(step["stream"]["chunks"]) if isinstance(c.get("usage"), dict)), None)
    return usage if isinstance(usage, dict) else {}


def usage_tokens(step):
    usage = _usage(step)
    prompt = usage.get("prompt_tokens", usage.get("input_tokens")) or 0
    completion = usage.get("completion_tokens", usage.get("output_tokens")) or 0
    return int(prompt), int(completion)


def _reasoning_tokens(usage):
    for path in ("completion_tokens_details.reasoning_tokens", "output_tokens_details.reasoning_tokens", "reasoning_tokens"):
        value = jsonpath.get(usage, path)
        if isinstance(value, int):
            return value
    return None


def _vendor_usage(chunk):
    found = None
    for key, value in chunk.items():
        if key not in STANDARD_CHUNK_KEYS and isinstance(value, dict):
            for path, leaf in jsonpath.walk(value, key):
                if isinstance(leaf, int) and not isinstance(leaf, bool) and re.search(r"usage|token", path, re.IGNORECASE):
                    found = path.rsplit(".", 1)[0] if "usage" not in path.rsplit(".", 1)[-1] else path
                    break
        if found:
            break
    return found


def _tool_calls(chunks):
    calls = {}
    for chunk in chunks:
        for choice in chunk.get("choices") or []:
            for delta in jsonpath.get(choice, "delta.tool_calls") or []:
                position = delta.get("index")
                call = calls.setdefault(
                    position if position is not None else len(calls),
                    {"deltas": 0, "arg_chunks": 0, "ids": 0, "index_present": True, "first_has_id": None,
                     "name": None, "arguments": ""})
                if position is None:
                    call["index_present"] = False
                if call["first_has_id"] is None:
                    call["first_has_id"] = bool(delta.get("id"))
                if delta.get("id"):
                    call["ids"] += 1
                function = delta.get("function") or {}
                if function.get("name"):
                    call["name"] = function["name"]
                fragment = function.get("arguments")
                if fragment:
                    call["arg_chunks"] += 1
                    call["arguments"] += fragment
                call["deltas"] += 1
    return calls


@derive("stream_summary")
def stream_summary(facts, args, ctx):
    step = facts[args["from"]]
    chunks = (step.get("stream") or {}).get("chunks", [])
    content, reasoning, finish = [], [], None
    usage_where, usage_value = "none", None
    for chunk in chunks:
        for choice in chunk.get("choices") or []:
            delta = choice.get("delta") or {}
            content.append(delta.get("content") or "")
            reasoning.append(delta.get("reasoning_content") or delta.get("reasoning") or "")
            finish = choice.get("finish_reason") or finish
        usage = chunk.get("usage")
        if isinstance(usage, dict) and usage:
            usage_where = "final-chunk" if not chunk.get("choices") else "content-chunk"
            usage_value = usage
    if usage_where == "none":
        for chunk in chunks:
            vendor = _vendor_usage(chunk)
            if vendor:
                usage_where = f"vendor-field:{vendor}"
                break
    calls = _tool_calls(chunks)
    first = calls[min(calls)] if calls else {}
    parsed = None
    if first:
        try:
            parsed = json.loads(first["arguments"])
        except ValueError:
            parsed = None
    return {
        "chunk_count": len(chunks), "done": (step.get("stream") or {}).get("done", False),
        "content": "".join(content), "reasoning": "".join(reasoning), "finish_reason": finish,
        "usage_where": usage_where, "usage": usage_value,
        "tool": {
            "present": bool(calls), "calls": len(calls), "name": first.get("name"),
            "arg_chunks": first.get("arg_chunks", 0), "incremental": first.get("arg_chunks", 0) > 1,
            "index_present": all(c["index_present"] for c in calls.values()) if calls else False,
            "first_has_id": bool(first.get("first_has_id")), "id_repeated": first.get("ids", 0) > 1,
            "arguments_valid": isinstance(parsed, dict),
        },
    }


@derive("reasoning_location")
def reasoning_location(facts, args, ctx):
    step = facts[args["from"]]
    message = _message(step)
    usage = _usage(step)
    tokens = _reasoning_tokens(usage)
    content = message.get("content") if isinstance(message.get("content"), str) else ""
    text, where = "", "none"
    if isinstance(message.get("reasoning_content"), str) and message["reasoning_content"]:
        where, text = "reasoning_content", message["reasoning_content"]
    elif isinstance(message.get("reasoning"), str) and message["reasoning"]:
        where, text = "reasoning", message["reasoning"]
    elif message.get("reasoning_details"):
        where, text = "reasoning_details", json.dumps(message["reasoning_details"])
    elif re.search(r"<think(ing)?>", content, re.IGNORECASE):
        where, text = "inline-think", content
    elif tokens:
        where = "tokens-only"
    return {"where": where, "chars": len(text), "tokens": tokens,
            "field": where if where in ("reasoning_content", "reasoning", "reasoning_details") else None}


@derive("effort_compare")
def effort_compare(facts, args, ctx):
    measures = {}
    for label in ("low", "high"):
        step = facts[args[label]]
        tokens = _reasoning_tokens(_usage(step))
        kind = "reasoning_tokens"
        if tokens is None:
            tokens = usage_tokens(step)[1]
            kind = "completion_tokens"
        measures[label] = (tokens, kind)
    low, high = measures["low"][0], measures["high"][0]
    capped = all(jsonpath.get(facts[args[label]], "json.choices[0].finish_reason") == "length" for label in ("low", "high"))
    return {"low": low, "high": high, "kind": measures["high"][1], "capped": capped,
            "effective": high >= max(low * 1.25, low + 20)}


@derive("cached_tokens")
def cached_tokens(facts, args, ctx):
    usage = _usage(facts[args["second"]])
    first_usage = _usage(facts[args["first"]])
    standard = jsonpath.get(usage, "prompt_tokens_details.cached_tokens")
    found = [(p, v) for p, v in jsonpath.walk(usage) if isinstance(v, int) and re.search(r"cach", p, re.IGNORECASE)]
    first_found = [(p, v) for p, v in jsonpath.walk(first_usage) if isinstance(v, int) and re.search(r"cach", p, re.IGNORECASE)]
    where = "prompt_tokens_details.cached_tokens" if isinstance(standard, int) else (found[0][0] if found else "none")
    value = standard if isinstance(standard, int) else (found[0][1] if found else None)
    return {"where": where, "value": value, "standard": isinstance(standard, int),
            "first_value": first_found[0][1] if first_found else None,
            "prompt_tokens": usage.get("prompt_tokens")}


@derive("json_content")
def json_content(facts, args, ctx):
    content = _message(facts[args["from"]]).get("content")
    text = content if isinstance(content, str) else ""
    fenced = FENCE.match(text)
    candidate = fenced.group(1) if fenced else text
    try:
        parsed = json.loads(candidate)
        ok = True
    except ValueError:
        parsed, ok = None, False
    return {"ok": ok, "fenced": bool(fenced), "is_object": isinstance(parsed, dict), "parsed": parsed, "length": len(text)}


@derive("schema_check")
def schema_check(facts, args, ctx):
    parsed = jsonpath.get(facts, args["from"] + ".parsed")
    errors = schemacheck.validate(parsed, ctx["constants"][args["schema"]]) if parsed is not None else ["no JSON"]
    return {"valid": not errors, "errors": errors[:3]}


@derive("tool_call")
def tool_call(facts, args, ctx):
    calls = _message(facts[args["from"]]).get("tool_calls") or []
    first = calls[0] if calls else {}
    function = first.get("function") or {}
    try:
        parsed = json.loads(function.get("arguments") or "")
    except ValueError:
        parsed = None
    return {"present": bool(calls), "id": first.get("id"), "name": function.get("name"),
            "arguments_valid": isinstance(parsed, dict), "count": len(calls),
            "finish_reason": jsonpath.get(facts[args["from"]], "json.choices[0].finish_reason")}


@derive("replay_message")
def replay_message(facts, args, ctx):
    message = _message(facts[args["from"]])
    field = facts[args["location"]]["field"]
    result = {"role": "assistant", "content": message.get("content") or ""}
    if field:
        result[field] = message.get(field)
    return {"message": result, "available": bool(field)}


@derive("error_shape")
def error_shape(facts, args, ctx):
    step = facts[args["from"]]
    body = step.get("json")
    if not isinstance(body, dict):
        shape = "non-json"
    elif isinstance(body.get("error"), dict) and isinstance(body["error"].get("message"), str):
        shape = "openai"
    elif isinstance(body.get("error"), str):
        shape = "error-string"
    elif isinstance(body.get("message"), str):
        shape = "flat-message"
    elif "detail" in body:
        shape = "detail"
    else:
        shape = "other"
    return {"shape": shape, "keys": sorted(body)[:6] if isinstance(body, dict) else []}


@derive("rate_limit_headers")
def rate_limit_headers(facts, args, ctx):
    names = sorted({n for headers in ctx["seen_headers"] for n in headers if RATE_LIMIT_NAME.search(n)})
    standard = [n for n in names if n.startswith("x-ratelimit-") and ("requests" in n or "tokens" in n)]
    return {"names": names, "standard": bool(standard)}


@derive("model_identity")
def model_identity(facts, args, ctx):
    returned = jsonpath.get(facts[args["from"]], "json.model")
    requested = ctx["models"][args.get("which", "chat")]
    if not isinstance(returned, str):
        relation = "missing"
    elif returned == requested:
        relation = "equal"
    elif returned.startswith(requested) or requested.startswith(returned):
        relation = "prefix"
    elif requested.split("/")[-1].lower() in returned.lower():
        relation = "contains"
    else:
        relation = "different"
    return {"requested": requested, "returned": returned, "relation": relation}


@derive("response_text")
def response_text(facts, args, ctx):
    body = jsonpath.get(facts[args["from"]], "json") or {}
    texts = []
    for item in body.get("output") or []:
        for part in (item.get("content") or []) if isinstance(item, dict) else []:
            if isinstance(part, dict) and isinstance(part.get("text"), str):
                texts.append(part["text"])
    return {"text": "".join(texts), "object": body.get("object"), "has_id": bool(body.get("id")),
            "store": body.get("store")}
