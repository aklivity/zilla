#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-echo-jwt --namespace zilla-http-echo-jwt
kubectl delete namespace zilla-http-echo-jwt
