#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-proxy --namespace zilla-http-proxy
kubectl delete namespace zilla-http-proxy
