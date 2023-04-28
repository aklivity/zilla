#!/bin/bash
set -ex

# Install Zilla and Kafka to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-grpc-kafka-echo chart --namespace zilla-grpc-kafka-echo --create-namespace --wait

# Create the echo-commands topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-grpc-kafka-echo --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-grpc-kafka-echo "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic echo-messages \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-grpc-kafka-echo service/zilla 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-grpc-kafka-echo service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
