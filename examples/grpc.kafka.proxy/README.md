# grpc.kafka.proxy

Listens on https port `7151` and uses kafka as proxy to talk to `grpc-echo` on tcp port `50051`.

## Requirements

- jq
- docker compose
- [grpcurl](https://github.com/fullstorydev/grpcurl)

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

## Verify behavior

### Unary Stream

Echo `{"message":"Hello World"}` message via unary rpc.

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
docker compose -p zilla-grpc-kafka-proxy exec kafkacat \
  kafkacat -C -b kafka.examples.dev:29092 -t echo-requests -J -u | jq .
```

output:

```json
{
  "topic": "echo-requests",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1683828554432,
  "broker": 1,
  "headers": [
    "zilla:service",
    "grpc.examples.echo.Echo",
    "zilla:method",
    "UnaryEcho",
    "zilla:reply-to",
    "echo-responses",
    "zilla:correlation-id",
    "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2"
  ],
  "key": "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-requests",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1683828554442,
  "broker": 1,
  "headers": [
    "zilla:service",
    "grpc.examples.echo.Echo",
    "zilla:method",
    "UnaryEcho",
    "zilla:reply-to",
    "echo-responses",
    "zilla:correlation-id",
    "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2"
  ],
  "key": "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
  "payload": null
}
% Reached end of topic echo-requests [0] at offset 2
```

### Bidirectional streaming

Echo messages via bidirectional streaming rpc.

```bash
grpcurl -plaintext -proto ./etc/protos/echo.proto -d @ \
    localhost:7151 grpc.examples.echo.Echo.BidirectionalStreamingEcho <<EOM
{"message":"Hello World, first"}
{"message":"Hello World, stream"}
{"message":"Hello World, stream"}
{"message":"Hello World, stream"}
{"message":"Hello World, last"}
EOM
```

output:

```json
{
  "message": "Hello World, first"
}
{
  "message": "Hello World, stream"
}
{
  "message": "Hello World, stream"
}
{
  "message": "Hello World, stream"
}
{
  "message": "Hello World, last"
}
```

Verify the message payloads arrived in order, followed by a tombstone to mark the end of the response.

```bash
docker compose -p zilla-grpc-kafka-proxy exec kafkacat \
  kafkacat -C -b kafka.examples.dev:29092 -t echo-responses -J -u | jq .
```

output:

```json
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 4,
  "tstype": "create",
  "ts": 1721162975117,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f"
  ],
  "key": "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
  "payload": "\n\u0012Hello World, first"
}
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 5,
  "tstype": "create",
  "ts": 1721162975117,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f"
  ],
  "key": "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
  "payload": "\n\u0013Hello World, stream"
}
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 6,
  "tstype": "create",
  "ts": 1721162975117,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f"
  ],
  "key": "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
  "payload": "\n\u0013Hello World, stream"
}
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 7,
  "tstype": "create",
  "ts": 1721162975117,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f"
  ],
  "key": "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
  "payload": "\n\u0013Hello World, stream"
}
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 8,
  "tstype": "create",
  "ts": 1721162975117,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f"
  ],
  "key": "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
  "payload": "\n\u0011Hello World, last"
}
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 9,
  "tstype": "create",
  "ts": 1721162975117,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
    "zilla:status",
    "0"
  ],
  "key": "d8b57dd3-d0e8-4b99-86ce-a7c79cd7d49a-46ae5cdb4b46cbb367cbad0bea36a56f",
  "payload": null
}
% Reached end of topic echo-responses [0] at offset 9
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
