#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-grpc-kafka-echo zilla-grpc-kafka-echo-kafka --namespace zilla-grpc-kafka-echo
kubectl delete namespace zilla-grpc-kafka-echo
