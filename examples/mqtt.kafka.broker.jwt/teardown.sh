#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-mqtt-kafka-broker-jwt zilla-mqtt-kafka-broker-jwt-kafka --namespace zilla-mqtt-kafka-broker-jwt
kubectl delete namespace zilla-mqtt-kafka-broker-jwt
