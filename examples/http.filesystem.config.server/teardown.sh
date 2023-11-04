#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
NAMESPACE=zilla-config-server
helm uninstall zilla-config zilla-http --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
