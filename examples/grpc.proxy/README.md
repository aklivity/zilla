# grpc.proxy

Listens on https port `7151` and will echo back whatever is published to `grpc-echo` on tcp port `50051`.

## Requirements

- docker compose
- [grpcurl](https://github.com/fullstorydev/grpcurl)

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

#### Unary Stream

Echo `{"message":"Hello World"}` message via unary rpc.

```bash
grpcurl -insecure -proto ./etc/protos/echo.proto  -d '{"message":"Hello World"}' \
    localhost:7153 grpc.examples.echo.Echo.UnaryEcho
```

output:

```json
{
  "message": "Hello World"
}
```

#### Bidirectional streaming

Echo messages via bidirectional streaming rpc.

```bash
grpcurl -insecure -proto ./etc/protos/echo.proto -d @ \
    localhost:7153 grpc.examples.echo.Echo.BidirectionalStreamingEcho
```

Paste below message.

```json
{
  "message": "Hello World"
}
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
