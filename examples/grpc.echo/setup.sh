#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-grpc-echo
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file configMaps.proto.data.echo\\.proto=proto/echo.proto \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7151 7153 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 7151; do sleep 1; done
until nc -z localhost 7153; do sleep 1; done
