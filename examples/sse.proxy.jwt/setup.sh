#!/bin/bash
set -ex

# Verify SSE Server image already available locally
docker image inspect zilla-examples/sse-server:latest --format 'Image Found {{.RepoTags}}'

# Install Zilla and SSE server to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-sse-proxy-jwt chart --namespace zilla-sse-proxy-jwt --create-namespace --wait

# Copy www files to the persistent volume mounted in the pod's filesystem
ZILLA_POD=$(kubectl get pods --namespace zilla-sse-proxy-jwt --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-sse-proxy-jwt www "$ZILLA_POD:/var/"

# Start port forwarding
kubectl port-forward --namespace zilla-sse-proxy-jwt service/zilla 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-sse-proxy-jwt service/sse-server 8001 7001 > /tmp/kubectl-sse-server.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
until nc -z localhost 8001; do sleep 1; done
