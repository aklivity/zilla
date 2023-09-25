#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-mqtt-kafka-broker zilla-mqtt-kafka-broker-kafka --namespace zilla-mqtt-kafka-broker
kubectl delete namespace zilla-mqtt-kafka-broker
