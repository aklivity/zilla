#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-grpc-echo chart --namespace zilla-grpc-echo --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-grpc-echo service/zilla 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
