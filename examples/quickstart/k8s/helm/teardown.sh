#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla
NAMESPACE="${NAMESPACE:-zilla-quickstart}"
helm uninstall zilla mqtt-simulator route-guide-server --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
