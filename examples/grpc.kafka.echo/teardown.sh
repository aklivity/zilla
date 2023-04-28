#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-grpc-kafka-echo --namespace zilla-grpc-kafka-echo
kubectl delete namespace zilla-grpc-kafka-echo
