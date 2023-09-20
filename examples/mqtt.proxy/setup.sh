#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
VERSION=0.9.46
helm install mqtt-proxy $ZILLA_CHART --version $VERSION --namespace mqtt-proxy --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install mosquitto mqtt broker to the Kubernetes cluster with helm and wait for the pod to start up
helm install mqtt-proxy-mosquitto chart --namespace mqtt-proxy --create-namespace --wait


# Start port forwarding
kubectl port-forward --namespace mqtt-proxy service/mqtt-proxy-zilla 1883 8883 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace mqtt-proxy service/mosquitto 1884 > /tmp/kubectl-mosquitto.log 2>&1 &
until nc -z localhost 1883; do sleep 1; done
until nc -z localhost 8883; do sleep 1; done
until nc -z localhost 1884; do sleep 1; done
