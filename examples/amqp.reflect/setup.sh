#!/bin/bash
set -ex

# Verify zilla:develop-SNAPSHOT image already available locally
docker image inspect ghcr.io/aklivity/zilla:develop-SNAPSHOT --format 'Image Found {{.RepoTags}}'

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-amqp-reflect
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7171 7172 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 7171; do sleep 1; done
until nc -z localhost 7172; do sleep 1; done
