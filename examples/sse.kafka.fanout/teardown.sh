#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-sse-kafka-fanout zilla-sse-kafka-fanout-kafka --namespace zilla-sse-kafka-fanout
kubectl delete namespace zilla-sse-kafka-fanout
