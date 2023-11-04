#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Redpanda
NAMESPACE=zilla-http-redpanda-sasl-scram
helm uninstall zilla redpanda --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
