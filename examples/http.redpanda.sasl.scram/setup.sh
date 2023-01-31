#!/bin/bash
set -ex

# Install Zilla and Redpanda to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-http-redpanda-sasl-scram chart --namespace zilla-http-redpanda-sasl-scram --create-namespace --wait

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
kubectl port-forward --namespace zilla-http-redpanda-sasl-scram service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-http-redpanda-sasl-scram service/redpanda 9092 > /tmp/kubectl-redpanda.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
