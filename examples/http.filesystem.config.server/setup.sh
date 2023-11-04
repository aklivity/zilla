#!/bin/bash
set -ex

# Install Zilla (config) to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-config-server
helm upgrade --install zilla-config $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values zilla-config/values.yaml \
    --set-file zilla\\.yaml=zilla-config/zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Copy web files to the persistent volume mounted in the Zilla (config) pod's filesystem
ZILLA_CONFIG_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=zilla-config -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace $NAMESPACE www "$ZILLA_CONFIG_POD:/var/"

# Install Zilla (http) to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install zilla-http $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values zilla-http/values.yaml \
    --set-file configMaps.prop.data.zilla\\.properties=zilla-http/zilla.properties

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla-config 7115 7144 > /tmp/kubectl-zilla-config.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/zilla-http 7114 7143 > /tmp/kubectl-zilla-http.log 2>&1 &
until nc -z localhost 7115; do sleep 1; done
until nc -z localhost 7144; do sleep 1; done
until nc -z localhost 7114; do sleep 1; done
until nc -z localhost 7143; do sleep 1; done
