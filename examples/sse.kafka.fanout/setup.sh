#!/bin/bash
set -ex

# Install Zilla and Kafka to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-sse-kafka-fanout chart --namespace zilla-sse-kafka-fanout --create-namespace --wait

# Copy web files to the persistent volume mounted in the pod's filesystem
ZILLA_POD=$(kubectl get pods --namespace zilla-sse-kafka-fanout --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-sse-kafka-fanout www "$ZILLA_POD:/var/"

# Creates the events topic in Kafka with the cleanup.policy=compact topic configuration
KAFKA_POD=$(kubectl get pods --namespace zilla-sse-kafka-fanout --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-sse-kafka-fanout "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic events \
        --config cleanup.policy=compact \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-sse-kafka-fanout service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-sse-kafka-fanout service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
