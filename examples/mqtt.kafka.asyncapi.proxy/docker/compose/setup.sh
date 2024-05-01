#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-mqtt-kafka-asyncapi-proxy}"
export ZILLA_VERSION="${ZILLA_VERSION:-latest}"
export KAFKA_BROKER="${KAFKA_BROKER:-kafka}"
export KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-host.docker.internal:9092}"
export KAFKA_PORT="${KAFKA_PORT:-9092}"
INIT_KAFKA="${INIT_KAFKA:-true}"

# Start or restart Zilla
if [[ -z $(docker-compose -p $NAMESPACE ps -q zilla) ]]; then
  echo "==== Running the $NAMESPACE example with $KAFKA_BROKER($KAFKA_BOOTSTRAP_SERVER) ===="
  docker-compose -p $NAMESPACE up -d

  # Create the mqtt topics in Kafka
  if [[ $INIT_KAFKA == true ]]; then
    docker run --rm bitnami/kafka:3.2 bash -c "
    echo 'Creating topics for $KAFKA_BOOTSTRAP_SERVER'
    /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic mqtt-messages
    /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic streetlights
    /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic mqtt-retained --config cleanup.policy=compact
    /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic mqtt-sessions --config cleanup.policy=compact
    "
  fi

else
  docker-compose -p $NAMESPACE restart --no-deps zilla
fi
