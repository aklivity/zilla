#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART="${ZILLA_CHART:-oci://ghcr.io/aklivity/charts/zilla}"
ZILLA_VERSION="${ZILLA_VERSION:-^0.9.0}"
NAMESPACE="${NAMESPACE:-zilla-tls-echo}"
helm upgrade --install zilla $ZILLA_CHART --version $ZILLA_VERSION --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 23456 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 23456; do sleep 1; done
