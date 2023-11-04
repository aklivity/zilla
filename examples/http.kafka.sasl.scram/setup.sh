#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-http-kafka-sasl-scram
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Kafka and Zookeeper to the Kubernetes cluster with helm and wait for the pods to start up
helm upgrade --install kafka chart --namespace $NAMESPACE --create-namespace --wait

# Create the events topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace $NAMESPACE "$KAFKA_POD" --container kafka -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic events \
        --if-not-exists

# create SCRAM credential (user)
kubectl exec --namespace $NAMESPACE "$KAFKA_POD" --container kafka -- \
    /opt/bitnami/kafka/bin/kafka-configs.sh \
        --bootstrap-server localhost:9092 \
        --alter \
        --add-config 'SCRAM-SHA-256=[iterations=8192,password=bitnami],SCRAM-SHA-512=[password=bitnami]' \
        --entity-type users --entity-name user

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7114 7143 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 7114; do sleep 1; done
until nc -z localhost 7143; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
