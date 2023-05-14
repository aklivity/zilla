#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and SSE Server
helm uninstall zilla-sse-proxy-jwt zilla-sse-proxy-jwt-sse --namespace zilla-sse-proxy-jwt
kubectl delete namespace zilla-sse-proxy-jwt
