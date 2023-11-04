#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
NAMESPACE=zilla-http-echo-jwt
helm uninstall zilla kafka --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
