#!/bin/bash
set -ex

# Install Zilla and Kafka to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-http-kafka-crud chart --namespace zilla-http-kafka-crud --create-namespace --wait

# Create the items-snapshots topic in Kafka with the cleanup.policy=compact topic configuration
KAFKA_POD=$(kubectl get pods --namespace zilla-http-kafka-crud --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-http-kafka-crud "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic items-snapshots \
        --config cleanup.policy=compact \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-http-kafka-crud service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-http-kafka-crud service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
