#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
helm uninstall zilla-mqtt-kafka-reflect-jwt zilla-mqtt-kafka-reflect-jwt-kafka --namespace zilla-mqtt-kafka-reflect-jwt
kubectl delete namespace zilla-mqtt-kafka-reflect-jwt
