#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-kafka-oneway --namespace zilla-http-kafka-oneway
kubectl delete namespace zilla-http-kafka-oneway
