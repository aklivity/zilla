#!/bin/bash
set -ex

# Install Zilla, Kafka and Zookeeper to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-http-kafka-sasl-scram chart --namespace zilla-http-kafka-sasl-scram --create-namespace --wait

# Create the events topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-http-kafka-sasl-scram --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-http-kafka-sasl-scram "$KAFKA_POD" --container kafka -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic events \
        --if-not-exists

# create SCRAM credential (user)
kubectl exec --namespace zilla-http-kafka-sasl-scram "$KAFKA_POD" --container kafka -- \
    /opt/bitnami/kafka/bin/kafka-configs.sh \
        --bootstrap-server localhost:9092 \
        --alter \
        --add-config 'SCRAM-SHA-256=[iterations=8192,password=bitnami],SCRAM-SHA-512=[password=bitnami]' \
        --entity-type users --entity-name user

# Start port forwarding
kubectl port-forward --namespace zilla-http-kafka-sasl-scram service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-http-kafka-sasl-scram service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
