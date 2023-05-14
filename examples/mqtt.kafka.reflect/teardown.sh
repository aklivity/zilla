#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-mqtt-kafka-reflect zilla-mqtt-kafka-reflect-kafka --namespace zilla-mqtt-kafka-reflect
kubectl delete namespace zilla-mqtt-kafka-reflect
