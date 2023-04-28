#!/bin/bash
set -ex

# Verify Grpc Echo image already available locally
docker image inspect zilla-examples/grpc-echo:latest --format 'Image Found {{.RepoTags}}'

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-grpc-proxy chart --namespace zilla-grpc-proxy --create-namespace --wait --timeout 2m

# Start port forwarding
kubectl port-forward --namespace zilla-grpc-proxy service/zilla 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-grpc-proxy service/grpc-echo 8080 > /tmp/kubectl-grpc-echo.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
until nc -z localhost 8080; do sleep 1; done
