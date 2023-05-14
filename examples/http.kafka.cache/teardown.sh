#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-http-kafka-cache zilla-http-kafka-cache-kafka --namespace zilla-http-kafka-cache
kubectl delete namespace zilla-http-kafka-cache
