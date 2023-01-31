#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-filesystem --namespace zilla-http-filesystem
kubectl delete namespace zilla-http-filesystem
