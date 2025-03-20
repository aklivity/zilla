# grpc.kafka.fanout

Listens on https port `7151` and fanout messages from `messages` topic in Kafka.

## Requirements

- protoc
- docker compose
- [grpcurl](https://github.com/fullstorydev/grpcurl)

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

#### Unreliable server streaming

Produce protobuf message to Kafka topic, repeat to produce multiple messages.

```bash
docker compose -p zilla-grpc-kafka-fanout exec kafkacat \
  kafkacat -P -b kafka.examples.dev:29092 -t messages -k -e /tmp/binary.data
```

Stream messages via server streaming rpc.

```bash
grpcurl -plaintext -proto ./etc/protos/fanout.proto -d '' \
    localhost:7151 example.FanoutService.FanoutServerStream
```

output:

```json
{
  "message": "test"
}
```

This output repeats for each message produced to Kafka.

#### Reliable server streaming

Build the reliable streaming client which uses `32767` field as last message id to send as metadata to resume streaming from last received message.

```bash
cd grpc.reliable.streaming/
./mvnw clean install
cd ..
```

Connect with the reliable streaming client.

```bash
java -jar grpc.reliable.streaming/target/grpc-example-develop-SNAPSHOT-jar-with-dependencies.jar
```

output:

```text
...
INFO: Found message: message: "test"
32767: "\001\002\000\002"
```

Simulate connection loss by stopping the `zilla` service in the `docker` stack.

```bash
docker compose -p zilla-grpc-kafka-fanout stop zilla
```

Simulate connection recovery by starting the `zilla` service again.

```bash
docker compose -p zilla-grpc-kafka-fanout start zilla
```

Then produce another protobuf message to Kafka, repeat to produce multiple messages.

```bash
docker compose -p zilla-grpc-kafka-fanout exec kafkacat \
  kafkacat -P -b kafka.examples.dev:29092 -t messages -k -e /tmp/binary.data
```

The reliable streaming client will recover and zilla deliver only the new message.

```text
...
INFO: Found message: message: "test"
32767: "\001\002\000\004"
```

This output repeats for each message produced to Kafka after the zilla service is restart.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
