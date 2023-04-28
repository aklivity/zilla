#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-grpc-proxy --namespace zilla-grpc-proxy
kubectl delete namespace zilla-grpc-proxy
