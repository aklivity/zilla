#!/bin/bash
set -ex

# Install Zilla and Kafka to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-http-kafka-async chart --namespace zilla-http-kafka-async --create-namespace --wait

# Create the items-requests and items-responses topics in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-http-kafka-async --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-http-kafka-async "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic items-requests \
        --if-not-exists
kubectl exec --namespace zilla-http-kafka-async "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic items-responses \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-http-kafka-async service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-http-kafka-async service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
