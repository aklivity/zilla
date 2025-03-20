# grpc.kafka.echo

Listens on https port `7151` and will exchange grpc message in protobuf format through the `echo-messages` topic in Kafka.

## Requirements

- jq
- docker compose
- [grpcurl](https://github.com/fullstorydev/grpcurl)
- [ghz](https://ghz.sh/docs/install)

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

#### Unary Stream

Echo `{"message":"Hello World"}` message via unary rpc using `grpcurl` client.

```bash
grpcurl -plaintext -proto ./etc/protos/echo.proto -d '{"message":"Hello World"}' \
    localhost:7151 grpc.examples.echo.Echo.UnaryEcho
```

output:

```json
{
  "message": "Hello World"
}
```

Verify the message payload, followed by a tombstone to mark the end of the request.

```bash
docker compose -p zilla-grpc-kafka-echo exec kafkacat \
  kafkacat -C -b kafka.examples.dev:29092 -t echo-messages -J -u | jq .
```

output:

```json
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1683827977709,
  "broker": 1,
  "headers": [
    "zilla:service",
    "grpc.examples.echo.Echo",
    "zilla:method",
    "UnaryEcho",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983"
  ],
  "key": "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1683827977742,
  "broker": 1,
  "headers": [
    "zilla:service",
    "grpc.examples.echo.Echo",
    "zilla:method",
    "UnaryEcho",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983"
  ],
  "key": "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983",
  "payload": null
}
% Reached end of topic echo-messages [0] at offset 2
```

#### Bidirectional Stream

Echo messages via bidirectional streaming rpc.

```bash
grpcurl -plaintext -proto ./etc/protos/echo.proto -d @ \
    localhost:7151 grpc.examples.echo.Echo.BidirectionalStreamingEcho
```

Paste below message.

```json
{
  "message": "Hello World"
}
```

Verify the message payloads, followed by a tombstone to mark the end of each request.

```bash
docker compose -p zilla-grpc-kafka-echo exec kafkacat \
  kafkacat -C -b kafka.examples.dev:29092 -t echo-messages -J -u | jq .
```

output:

```json
...
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 2,
  "tstype": "create",
  "ts": 1683828250706,
  "broker": 1,
  "headers": [
    "zilla:service",
    "grpc.examples.echo.Echo",
    "zilla:method",
    "BidirectionalStreamingEcho",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef"
  ],
  "key": "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 3,
  "tstype": "create",
  "ts": 1683828252352,
  "broker": 1,
  "headers": [
    "zilla:service",
    "grpc.examples.echo.Echo",
    "zilla:method",
    "BidirectionalStreamingEcho",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef"
  ],
  "key": "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef",
  "payload": null
}
% Reached end of topic echo-messages [0] at offset 4
```

### Bench

```bash
ghz --config bench.json \
    --proto ./etc/protos/echo.proto \
    --call grpc.examples.echo.Echo/BidirectionalStreamingEcho \
    localhost:7151
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
