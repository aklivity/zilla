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

import http.client
import json
import ssl
import time
from urllib.parse import urlsplit

MAX_RETRY_WAIT = 20
GATEWAY_ERRORS = (502, 503, 504)
MAX_STREAM_BYTES = 2_000_000


def _connect(parts, timeout):
    port = parts.port
    if parts.scheme == "https":
        conn = http.client.HTTPSConnection(parts.hostname, port, timeout=timeout, context=ssl.create_default_context())
    else:
        conn = http.client.HTTPConnection(parts.hostname, port, timeout=timeout)
    return conn


def _read_sse(response):
    chunks, bad, done, received = [], 0, False, 0
    data = []
    while True:
        line = response.readline()
        if not line:
            break
        received += len(line)
        if received > MAX_STREAM_BYTES:
            break
        text = line.decode("utf-8", "replace").rstrip("\r\n")
        if text.startswith("data:"):
            data.append(text[5:].lstrip())
        elif text == "" and data:
            payload = "\n".join(data)
            data = []
            if payload == "[DONE]":
                done = True
                break
            try:
                chunks.append(json.loads(payload))
            except ValueError:
                bad += 1
    if data and not done:
        payload = "\n".join(data)
        try:
            chunks.append(json.loads(payload))
        except ValueError:
            bad += 1
    return {"chunks": chunks, "bad_chunks": bad, "done": done, "sse": True}


def _once(method, url, headers, body, stream, timeout):
    parts = urlsplit(url)
    target = parts.path + (f"?{parts.query}" if parts.query else "")
    payload = None if body is None else json.dumps(body).encode()
    sent = dict(headers)
    if payload is not None:
        sent.setdefault("Content-Type", "application/json")
    started = time.monotonic()
    result = {"status": None, "headers": {}, "json": None, "text": "", "error": None, "stream": None}
    conn = _connect(parts, timeout)
    try:
        conn.request(method, target, body=payload, headers=sent)
        response = conn.getresponse()
        result["status"] = response.status
        result["headers"] = {k.lower(): v for k, v in response.getheaders()}
        kind = result["headers"].get("content-type", "")
        if stream and response.status == 200 and "event-stream" in kind:
            result["stream"] = _read_sse(response)
        else:
            raw = response.read(MAX_STREAM_BYTES).decode("utf-8", "replace")
            result["text"] = raw
            try:
                result["json"] = json.loads(raw)
            except ValueError:
                result["json"] = None
    except (OSError, http.client.HTTPException, ssl.SSLError) as exc:
        result["error"] = f"{type(exc).__name__}: {exc}"
    finally:
        conn.close()
    result["elapsed_ms"] = int((time.monotonic() - started) * 1000)
    return result


def send(method, url, headers, body=None, stream=False, timeout=90, retries=2, sleep=time.sleep):
    result = _once(method, url, headers, body, stream, timeout)
    attempt = 0
    while result["status"] == 429 and attempt < retries:
        wait = result["headers"].get("retry-after", "")
        delay = min(float(wait), MAX_RETRY_WAIT) if wait.replace(".", "", 1).isdigit() else 2 ** (attempt + 1)
        sleep(delay)
        attempt += 1
        result = _once(method, url, headers, body, stream, timeout)
    retryable = result["error"] is not None and not result["error"].startswith("TimeoutError")
    if (retryable or result["status"] in GATEWAY_ERRORS) and retries:
        sleep(1)
        result = _once(method, url, headers, body, stream, timeout)
        attempt += 1
    result["retries"] = attempt
    return result
