# Zilla examples catalog

Source: https://github.com/aklivity/zilla/tree/develop/examples (also mirrored at
https://github.com/aklivity/zilla-examples). Every example is a self-contained folder with the
same shape:

```
<example.name>/
├── README.md          # description, requirements, verify-behavior curl/client commands
├── compose.yaml        # docker compose stack: zilla (+ kafka, kafka-ui, mock backends, etc.)
├── etc/zilla.yaml      # the actual Zilla gateway config (bindings, routes, telemetry)
└── .github/test.sh     # smoke test used in CI — a great source of copy/paste verify commands
```

Some examples add extra files: `etc/protos/*.proto` (grpc examples), `etc/specs/*.yaml`
(openapi/asyncapi examples), `private.pem` / JWT keys (`*.jwt` examples), TLS keystores
(`tls.*`, `*.p12`), or a small client app (`mcp.proxy/url-elicit`).

Default Zilla image is `ghcr.io/aklivity/zilla:latest`. Override with the `ZILLA_VERSION` env var.
Default project namespace can be overridden with `NAMESPACE` env var (see each compose.yaml).

## Table

| Example directory | Protocol focus | What it demonstrates | Host ports (host:container) | Extra requirements |
|---|---|---|---|---|
| `amqp.reflect` | AMQP | Echoes/broadcasts AMQP messages to all receiving clients | 7172:7172 | docker compose only |
| `asyncapi.http.kafka.proxy` | HTTP→Kafka, AsyncAPI | REST endpoint generated from an AsyncAPI spec, produces/fetches Kafka synchronously | 7114, 9092, 8080 (Kafka UI) | docker compose only |
| `asyncapi.mqtt.kafka.proxy` | MQTT→Kafka, AsyncAPI | Mediates MQTT broker messages onto Kafka topics defined via AsyncAPI | 7183, 9092, 8080 | docker compose only |
| `asyncapi.mqtt.proxy` | MQTT, AsyncAPI | Forwards MQTT publishes/subscribes to a Mosquitto broker per an AsyncAPI spec | 7183, 1883 | docker compose only |
| `asyncapi.sse.kafka.proxy` | SSE→Kafka, AsyncAPI | SSE API backed by Kafka, validated against AsyncAPI spec | 7114, 9092, 8080 | docker compose only |
| `asyncapi.sse.proxy` | SSE, AsyncAPI | Streams whatever an upstream SSE server publishes, per AsyncAPI spec | 7114, 8001, 7001 | docker compose only |
| `grpc.echo` | gRPC | Echoes unary + bidi-stream gRPC messages | 7151 | `grpcurl`, `ghz` (benchmarking) |
| `grpc.kafka.echo` | gRPC↔Kafka | Round-trips protobuf gRPC messages through a Kafka topic | 7151, 9092, 8080 | `grpcurl` |
| `grpc.kafka.fanout` | gRPC↔Kafka | Streams Kafka topic messages to many gRPC clients, log-compaction conflation | 7151, 9092, 8080 | `grpcurl` |
| `grpc.kafka.proxy` | gRPC↔Kafka | Correlates gRPC request/response over separate Kafka topics | 7151, 50051, 9092, 8080 | `grpcurl` |
| `grpc.proxy` | gRPC | Proxies gRPC requests/responses to a backend gRPC echo service | 7151, 7153, 50051 | `grpcurl` |
| `http.echo` | HTTP | Echoes whatever is POSTed | 7114 | docker compose only |
| `http.filesystem` | HTTP | Serves static files from a directory mounted in the Zilla container | 7114 | docker compose only |
| `http.json.schema` | HTTP | Proxies to nginx, enforcing JSON schema validation on requests | 7114, 80 | docker compose only |
| `http.kafka.async` | HTTP↔Kafka | Correlates HTTP req/response over separate Kafka topics, asynchronously (200 vs 202) | 7114, 9092, 8080 | docker compose only |
| `http.kafka.avro.json` | HTTP↔Kafka, Avro | Validates messages against Karapace Schema Registry on produce/fetch | 7114, 9092, 8080, 8081 | docker compose only |
| `http.kafka.cache` | HTTP↔Kafka | Serves cached HTTP responses from a Kafka topic, detects updates via etag | 7114, 9092, 8080 | docker compose only |
| `http.kafka.crud` | HTTP↔Kafka | Full REST CRUD API backed by a log-compacted Kafka topic acting as a table | 7114, 9092, 8080 | docker compose only |
| `http.kafka.oneway` | HTTP→Kafka | Fire-and-forget produce to Kafka over SASL-SCRAM/TLS | 7114, 9092, 8080 | docker compose only |
| `http.kafka.oneway.oauthbearer` | HTTP→Kafka, OAuth | Same as oneway but Kafka auth uses a pre-signed OAuth bearer token | 7114, 9092, 8080 | uses token baked into compose.yaml (valid till 2096) |
| `http.kafka.proto.json` | HTTP↔Kafka, Protobuf | JSON-in over REST, validated + converted to Protobuf on produce | 7114, 9092, 8080 | docker compose only |
| `http.kafka.proto.oneway` | HTTP→Kafka, Protobuf | JSON-in over REST, fire-and-forget produce as Protobuf | 7114, 9092, 8080 | docker compose only |
| `http.kafka.sync` | HTTP↔Kafka | Correlates HTTP req/response over separate Kafka topics, synchronously | 7114, 9092, 8080 | docker compose only |
| `http.metrics` | HTTP, Observability | Exposes HTTP metrics (method, path) via Prometheus | 7114, 7190 | docker compose only |
| `http.proxy` | HTTP, TLS | TLS-terminating proxy to an nginx backend | 7143, 443 | docker compose only |
| `http.proxy.jwt` | HTTP, JWT | Rejects/accepts requests based on JWT validation, no info leak on failure | 7114 | ships a `private.pem` used to mint test tokens |
| `mcp.proxy` | MCP | Proxies a Model Context Protocol server, demonstrates URL elicitation | 7114, 7190, 3003 | Node.js client app in `url-elicit/` (`npm install && node client.mjs`) |
| `mqtt.kafka.proxy` | MQTT↔Kafka | Mediates MQTT broker publish/subscribe onto Kafka topics | 7183, 9092, 8080 | docker compose only |
| `mqtt.proxy.jwt` | MQTT, JWT | Rejects/accepts MQTT connections based on JWT validation | 7183, 1883 | ships JWT signing key |
| `openapi.asyncapi.kakfa.proxy` | HTTP→Kafka, OpenAPI+AsyncAPI | REST-to-Kafka proxy generated from combined OpenAPI + AsyncAPI specs | 7114, 4444, 9092, 8080 | docker compose only |
| `openapi.proxy` | HTTP, OpenAPI | Proxies requests defined in a Petstore OpenAPI schema | 7114, 8000 | docker compose only |
| `sse.kafka.fanout` | SSE↔Kafka | Streams a Kafka topic to many SSE clients with log-compaction conflation | 7114, 9092, 8080 | docker compose only |
| `sse.kafka.fanout.jwt` | SSE↔Kafka, JWT | Same as sse.kafka.fanout but streams require a valid JWT | 7143, 9092, 8080 | ships JWT signing key |
| `sse.proxy.jwt` | SSE, JWT | SSE proxy enforcing streaming security via JWT, with challenge/refresh flow | 7143, 8001, 7001 | ships JWT signing key |
| `tcp.echo` | TCP | Echoes bytes sent to the server | 12345 | docker compose only |
| `tcp.reflect` | TCP | Echoes bytes, broadcasting to all connected clients | 12345 | docker compose only |
| `tls.echo` | TLS | Echoes encrypted bytes over TLS | 23456 | ships a pre-generated `localhost.p12` keystore (regen script included) |
| `tls.reflect` | TLS | Echoes encrypted bytes over TLS, broadcasting to all clients | 23456 | ships a pre-generated `localhost.p12` keystore |
| `ws.echo` | WebSocket | Echoes messages sent over WebSocket | 7114 | docker compose only |
| `ws.proxy` | WebSocket | Tests window-update flow after WS upgrade through HTTP binding | 7143 | docker compose only |
| `ws.reflect` | WebSocket | Echoes WS messages, broadcasting to all clients | 7114 | docker compose only |

## Picking an example

Match the user's ask to the table above:
- Mentions **Kafka + REST/HTTP** → one of the `http.kafka.*` examples (crud, cache, oneway, sync/async, avro/protobuf variants).
- Mentions **gRPC** → `grpc.*` (add `.kafka.` if they also want Kafka involved).
- Mentions **MQTT** → `mqtt.*` or `asyncapi.mqtt.*`.
- Mentions **SSE / server-sent events / streaming to browser** → `sse.*` or `asyncapi.sse.*`.
- Mentions **auth / JWT / OAuth** → the `*.jwt` or `*.oauthbearer` variant of the closest protocol match.
- Mentions **OpenAPI or AsyncAPI spec-driven config** → `openapi.*` / `asyncapi.*`.
- Mentions **plain TCP/TLS/WebSocket echo, or "simplest possible example"** → `tcp.echo`, `tls.echo`, `ws.echo`.
- Mentions **static files** → `http.filesystem`.
- Mentions **metrics / observability / Prometheus** → `http.metrics`.
- Mentions **AMQP** → `amqp.reflect`.
- Mentions **MCP / Model Context Protocol** → `mcp.proxy`.
- Ambiguous / "just show me Zilla" → default to `http.kafka.crud` (the one used in Zilla's own quickstart) since it's the best single example of Zilla's core value prop (REST-to-Kafka gateway).
  If nothing matches well, say so and ask which protocol/use case they care about rather than guessing.
