#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART="${ZILLA_CHART:-oci://ghcr.io/aklivity/charts/zilla}"
ZILLA_VERSION="${ZILLA_VERSION:-^0.9.0}"
NAMESPACE="${NAMESPACE:-zilla-asyncapi-sse-proxy}"
helm upgrade --install zilla $ZILLA_CHART --version $ZILLA_VERSION --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file configMaps.asyncapi.data.sse-asyncapi\\.yaml=sse-asyncapi.yaml

# Install SSE server to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install sse-server chart --namespace $NAMESPACE --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7114 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/sse-server 8001 7001 > /tmp/kubectl-sse-server.log 2>&1 &
until nc -z localhost 7114; do sleep 1; done
until nc -z localhost 8001; do sleep 1; done
until nc -z localhost 7001; do sleep 1; done
