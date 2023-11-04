#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and SSE Server
NAMESPACE=zilla-sse-proxy-jwt
helm uninstall zilla sse-server --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
