#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Grpc Echo
NAMESPACE=zilla-grpc-proxy
helm uninstall zilla grpc-echo --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
