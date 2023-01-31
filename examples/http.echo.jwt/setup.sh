#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-http-echo-jwt chart --namespace zilla-http-echo-jwt --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-http-echo-jwt service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
