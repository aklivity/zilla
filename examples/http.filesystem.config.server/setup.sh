#!/bin/bash
set -ex

# Install Zilla (config) to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
helm install zilla-config-server-config $ZILLA_CHART --namespace zilla-config-server --create-namespace --wait \
    --values zilla-config/values.yaml \
    --set-file zilla\\.yaml=zilla-config/zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Copy web files to the persistent volume mounted in the Zilla (config) pod's filesystem
ZILLA_CONFIG_POD=$(kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config-server-config -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-config-server www "$ZILLA_CONFIG_POD:/var/"

# Install Zilla (http) to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-config-server-http $ZILLA_CHART --namespace zilla-config-server --create-namespace --wait \
    --values zilla-http/values.yaml \
    --set-file configMaps.prop.data.zilla\\.properties=zilla-http/zilla.properties

# Wait for Zilla (http) to pick up the config from Zilla (config)
kubectl run busybox-pod --image=busybox:1.28 --namespace zilla-config-server --rm --restart=Never -i -t -- /bin/sh -c 'until nc -w 2 zilla-config-server-http 8080; do echo . && sleep 5; done' > /dev/null 2>&1
kubectl wait --namespace zilla-config-server --for=delete pod/busybox-pod

# Start port forwarding
kubectl port-forward --namespace zilla-config-server service/zilla-config-server-config 8081 9091 > /tmp/kubectl-zilla-config.log 2>&1 &
kubectl port-forward --namespace zilla-config-server service/zilla-config-server-http 8080 9090 > /tmp/kubectl-zilla-http.log 2>&1 &
until nc -z localhost 8081; do sleep 1; done
until nc -z localhost 8080; do sleep 1; done
