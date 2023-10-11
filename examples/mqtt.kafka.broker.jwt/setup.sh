#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
helm install zilla-mqtt-kafka-broker-jwt $ZILLA_CHART --namespace zilla-mqtt-kafka-broker-jwt --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Kafka to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-mqtt-kafka-broker-jwt-kafka chart --namespace zilla-mqtt-kafka-broker-jwt --create-namespace --wait

# Create the mqtt-messages topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-mqtt-kafka-broker-jwt --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-mqtt-kafka-broker-jwt "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic mqtt-messages \
        --if-not-exists

kubectl exec --namespace zilla-mqtt-kafka-broker-jwt "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic mqtt-retained \
        --config "cleanup.policy=compact" \
        --if-not-exists

kubectl exec --namespace zilla-mqtt-kafka-broker-jwt "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic mqtt-sessions \
        --config "cleanup.policy=compact" \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-mqtt-kafka-broker-jwt service/zilla-mqtt-kafka-broker-jwt 1883 8883 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-mqtt-kafka-broker-jwt service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 1883; do sleep 1; done
until nc -z localhost 8883; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
