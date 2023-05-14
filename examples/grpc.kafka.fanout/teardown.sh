#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-grpc-kafka-fanout zilla-grpc-kafka-fanout-kafka --namespace zilla-grpc-kafka-fanout
kubectl delete namespace zilla-grpc-kafka-fanout
