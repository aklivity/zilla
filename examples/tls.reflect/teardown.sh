#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-tls-reflect --namespace zilla-tls-reflect
kubectl delete namespace zilla-tls-reflect
