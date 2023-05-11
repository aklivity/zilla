#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-mqtt-kafka chart --namespace zilla-mqtt-kafka --create-namespace --wait

# Create the mqtt_messages topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-mqtt-kafka --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-mqtt-kafka "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic mqtt_messages \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-mqtt-kafka service/zilla 1883 8883 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-mqtt-kafka service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &

until nc -z localhost 1883; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done