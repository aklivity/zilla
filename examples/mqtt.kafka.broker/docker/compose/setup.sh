#!/bin/bash
set -e

[[ -z "$KAFKA_HOST" && -z "$KAFKA_PORT" ]] && printf "==== This example requires a running kafka instance ====\n$USAGE" && exit 0;

NAMESPACE=zilla-mqtt-kafka-broker

# Start or restart Zilla
if [[ -z `docker-compose -p $NAMESPACE ps -q zilla` ]]; then
  docker-compose -p $NAMESPACE up -d

  # Create the mqtt topics in Kafka
  docker run --rm bitnami/kafka:3.2 bash -c "
  echo 'Creating topics for $KAFKA_HOST:$KAFKA_PORT'
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-sessions
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-messages --config cleanup.policy=compact
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-retained --config cleanup.policy=compact
  "

else
  docker-compose -p $NAMESPACE restart --no-deps zilla
fi
