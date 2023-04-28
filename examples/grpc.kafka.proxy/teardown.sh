#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-grpc-kafka-proxy --namespace zilla-grpc-kafka-proxy
kubectl delete namespace zilla-grpc-kafka-proxy
