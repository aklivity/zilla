#!/bin/bash
set -e

if [[ -z "$KAFKA_HOST" && -z "$KAFKA_PORT" ]]; then
  export KAFKA_HOST=host.docker.internal
  export KAFKA_PORT=9092
  echo "==== This example requires env vars KAFKA_HOST and KAFKA_PORT for a running kafka instance. Setting to the default ($KAFKA_HOST:$KAFKA_PORT) ===="
fi

NAMESPACE=zilla-quickstart

# Start or restart Zilla
if [[ -z $(docker-compose -p $NAMESPACE ps -q zilla) ]]; then
  docker-compose -p $NAMESPACE up -d

  docker run --rm bitnami/kafka bash -c "
  echo 'Creating topics for $KAFKA_HOST:$KAFKA_PORT'
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic items-crud --config cleanup.policy=compact
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic events-sse --config cleanup.policy=compact
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic echo-service-messages
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic route-guide-requests
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic route-guide-responses
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic iot-messages
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic iot-retained --config cleanup.policy=compact
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic iot-sessions --config cleanup.policy=compact
  "

else
  docker-compose -p $NAMESPACE restart --no-deps zilla
fi
