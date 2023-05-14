#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Nginx
helm uninstall zilla-http-proxy zilla-http-proxy-nginx --namespace zilla-http-proxy
kubectl delete namespace zilla-http-proxy
