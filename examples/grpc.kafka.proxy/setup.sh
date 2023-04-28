#!/bin/bash
set -ex

# Verify Grpc Echo image already available locally
docker image inspect zilla-examples/grpc-echo:latest --format 'Image Found {{.RepoTags}}'

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-grpc-kafka-proxy chart --namespace zilla-grpc-kafka-proxy --create-namespace --wait --timeout 2m

# Create the requests and responses topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-grpc-kafka-proxy --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-grpc-kafka-proxy "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic echo-requests \
        --if-not-exists

KAFKA_POD=$(kubectl get pods --namespace zilla-grpc-kafka-proxy --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-grpc-kafka-proxy "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic echo-responses \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-grpc-kafka-proxy service/zilla 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-grpc-kafka-proxy service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
kubectl port-forward --namespace zilla-grpc-kafka-proxy service/grpc-echo 8080 > /tmp/kubectl-grpc-echo.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
until nc -z localhost 8080; do sleep 1; done
