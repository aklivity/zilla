#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla, Prometheus and Prometheus Adapter
helm uninstall zilla-kubernetes-prometheus-autoscale zilla-kubernetes-prometheus-autoscale-prometheus --namespace zilla-kubernetes-prometheus-autoscale
kubectl delete namespace zilla-kubernetes-prometheus-autoscale
