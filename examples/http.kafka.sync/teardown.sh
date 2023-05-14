#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-http-kafka-sync zilla-http-kafka-sync-kafka --namespace zilla-http-kafka-sync
kubectl delete namespace zilla-http-kafka-sync
