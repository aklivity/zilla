#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-kubernetes-prometheus-autoscale --namespace zilla-kubernetes-prometheus-autoscale
kubectl delete namespace zilla-kubernetes-prometheus-autoscale
