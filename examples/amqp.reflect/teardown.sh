#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
NAMESPACE="${NAMESPACE:-zilla-amqp-reflect}"
helm uninstall zilla --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
