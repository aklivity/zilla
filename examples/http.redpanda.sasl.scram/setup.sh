#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-http-redpanda-sasl-scram
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Redpanda to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install redpanda chart --namespace $NAMESPACE --create-namespace --wait

# Create the user "user"
REDPANDA_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=redpanda -o name)
kubectl exec --namespace $NAMESPACE "$REDPANDA_POD" -- \
        rpk acl user \
        create user \
        -p redpanda

# Create the events topic
kubectl exec --namespace $NAMESPACE "$REDPANDA_POD" -- \
    rpk topic create events \
                --user user \
                --password redpanda \
                --sasl-mechanism SCRAM-SHA-256

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7114 7143 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/redpanda 9092 > /tmp/kubectl-redpanda.log 2>&1 &
until nc -z localhost 7114; do sleep 1; done
until nc -z localhost 7143; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
