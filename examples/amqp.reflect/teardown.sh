#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-amqp-reflect --namespace zilla-amqp-reflect
kubectl delete namespace zilla-amqp-reflect
