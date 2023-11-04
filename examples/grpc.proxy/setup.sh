#!/bin/bash
set -ex

# Verify Grpc Echo image already available locally
docker image inspect zilla-examples/grpc-echo:latest --format 'Image Found {{.RepoTags}}'

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-grpc-proxy
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file configMaps.proto.data.echo\\.proto=proto/echo.proto \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Grpc Echo to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install grpc-echo chart --namespace $NAMESPACE --wait --timeout 2m

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7153 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/grpc-echo 8080 > /tmp/kubectl-grpc-echo.log 2>&1 &
until nc -z localhost 7153; do sleep 1; done
until nc -z localhost 8080; do sleep 1; done
