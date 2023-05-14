#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
VERSION=0.9.46
helm install zilla-http-redpanda-sasl-scram $ZILLA_CHART --version $VERSION --namespace zilla-http-redpanda-sasl-scram --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Redpanda to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-http-redpanda-sasl-scram-redpanda chart --namespace zilla-http-redpanda-sasl-scram --create-namespace --wait

# Create the user "user"
REDPANDA_POD=$(kubectl get pods --namespace zilla-http-redpanda-sasl-scram --selector app.kubernetes.io/instance=redpanda -o name)
kubectl exec --namespace zilla-http-redpanda-sasl-scram "$REDPANDA_POD" -- \
        rpk acl user \
        create user \
        -p redpanda

# Create the events topic
kubectl exec --namespace zilla-http-redpanda-sasl-scram "$REDPANDA_POD" -- \
    rpk topic create events \
                --user user \
                --password redpanda \
                --sasl-mechanism SCRAM-SHA-256

# Start port forwarding
kubectl port-forward --namespace zilla-http-redpanda-sasl-scram service/zilla-http-redpanda-sasl-scram 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-http-redpanda-sasl-scram service/redpanda 9092 > /tmp/kubectl-redpanda.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
