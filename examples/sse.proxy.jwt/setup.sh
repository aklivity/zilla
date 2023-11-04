#!/bin/bash
set -ex

# Verify SSE Server image already available locally
docker image inspect zilla-examples/sse-server:latest --format 'Image Found {{.RepoTags}}'

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-sse-proxy-jwt
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install SSE server to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install sse-server chart --namespace $NAMESPACE --create-namespace --wait

# Copy www files to the persistent volume mounted in the pod's filesystem
ZILLA_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace $NAMESPACE www "$ZILLA_POD:/var/"

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7143 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/sse-server 8001 7001 > /tmp/kubectl-sse-server.log 2>&1 &
until nc -z localhost 7143; do sleep 1; done
until nc -z localhost 8001; do sleep 1; done
until nc -z localhost 7001; do sleep 1; done
