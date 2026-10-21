#!/bin/sh
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

# Starts a local OpenAI-compatible host in a container named llm-host and waits until it serves requests.
# Prints "<ENV>=<value>" lines for the model names to probe, to be appended to $GITHUB_ENV.
set -eu

host="$1"
wait_for() {
  url="$1"
  limit="$2"
  elapsed=0
  until curl -fsS -o /dev/null "$url"; do
    elapsed=$((elapsed + 5))
    if [ "$elapsed" -ge "$limit" ]; then
      echo "host did not become ready within ${limit}s" >&2
      docker logs --tail 40 llm-host >&2 || true
      exit 1
    fi
    sleep 5
  done
}

case "$host" in
  ollama)
    model="hf.co/ggml-org/Qwen3-1.7B-GGUF:Q4_K_M"
    docker run -d --name llm-host --network host ollama/ollama >/dev/null
    wait_for http://127.0.0.1:11434/api/version 120
    docker exec llm-host ollama pull "$model" >&2
    echo "LLM_PROBE_CHAT_MODEL=$model"
    echo "LLM_PROBE_REASONING_MODEL=$model"
    ;;
  llamacpp)
    docker run -d --name llm-host --network host -v "$HOME/.cache/llama.cpp:/root/.cache" \
      ghcr.io/ggml-org/llama.cpp:server -hf ggml-org/Qwen3-1.7B-GGUF:Q4_K_M --host 0.0.0.0 --port 8080 \
      --jinja -c 8192 --alias qwen3-1.7b >/dev/null
    wait_for http://127.0.0.1:8080/health 900
    echo "LLM_PROBE_CHAT_MODEL=qwen3-1.7b"
    echo "LLM_PROBE_REASONING_MODEL=qwen3-1.7b"
    ;;
  vllm)
    docker run -d --name llm-host --network host --cap-add SYS_NICE --security-opt seccomp=unconfined \
      -v "$HOME/.cache/huggingface:/root/.cache/huggingface" vllm/vllm-openai-cpu:latest-x86_64 \
      Qwen/Qwen3-0.6B --reasoning-parser qwen3 --max-model-len 4096 >/dev/null
    wait_for http://127.0.0.1:8000/v1/models 900
    echo "LLM_PROBE_CHAT_MODEL=Qwen/Qwen3-0.6B"
    echo "LLM_PROBE_REASONING_MODEL=Qwen/Qwen3-0.6B"
    ;;
  sglang)
    docker run -d --name llm-host --network host --ipc=host -v "$HOME/.cache/huggingface:/root/.cache/huggingface" \
      lmsysorg/sglang:v0.5.22-xeon sglang serve --model-path Qwen/Qwen3-0.6B --device cpu --disable-overlap-schedule \
      --trust-remote-code --host 0.0.0.0 --port 30000 --tp 1 --reasoning-parser qwen3 >/dev/null
    wait_for http://127.0.0.1:30000/v1/models 900
    echo "LLM_PROBE_CHAT_MODEL=Qwen/Qwen3-0.6B"
    echo "LLM_PROBE_REASONING_MODEL=Qwen/Qwen3-0.6B"
    ;;
  *)
    echo "unknown local host: $host" >&2
    exit 2
    ;;
esac
