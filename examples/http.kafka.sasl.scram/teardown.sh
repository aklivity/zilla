#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-http-kafka-sasl-scram zilla-http-kafka-sasl-scram-kafka --namespace zilla-http-kafka-sasl-scram
kubectl delete namespace zilla-http-kafka-sasl-scram
