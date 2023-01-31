#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-sse-proxy-jwt --namespace zilla-sse-proxy-jwt
kubectl delete namespace zilla-sse-proxy-jwt
