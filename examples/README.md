# Zilla Examples

[![Slack Community][community-image]][community-join]

This repo contains a collection of [example folders](#examples) that can be used individually to demonstrate key Zilla features. If this is your first step on your journey with Zilla, we encourage you to try our [Quickstart](https://docs.aklivity.io/zilla/latest/how-tos/quickstart/).

## Prerequisites

[![Docker]][docker-install][![Postman]][postman-url]

You will need an environment with [Docker][docker-install] installed. Check out our [Postman collections][postman-url] for more ways to interact with an example.

## Getting Started

Install and run any of the [examples](#examples) using `docker compose`:

```bash
docker compose --project-directory <example.name> up -d
```

![demo](.assets/demo.gif)

> [!NOTE]
Make sure you have the `latest` version of Zilla by running the `docker pull ghcr.io/aklivity/zilla:latest` command. To specify a specific Zilla image version you can set the `ZILLA_VERSION` environment variable before running an example.

## Examples

| Name                                                         | Description                                                                               |
|--------------------------------------------------------------| ----------------------------------------------------------------------------------------- |
| [amqp.reflect](amqp.reflect)                                 | Echoes messages published to the AMQP server, broadcasting to all receiving AMQP clients  |
| [asyncapi.http.kafka.proxy](asyncapi.http.kafka.proxy)       | Forwards validated MQTT publish messages and proxies subscribes to an MQTT broker         |
| [asyncapi.mqtt.kafka.proxy](asyncapi.mqtt.kafka.proxy)       | Forwards MQTT publish messages to Kafka, broadcasting to all subscribed MQTT clients      |
| [asyncapi.mqtt.proxy](asyncapi.mqtt.proxy)                   | Correlates HTTP requests and responses over separate Kafka topics                         |
| [asyncapi.sse.kafka.proxy](asyncapi.sse.kafka.proxy)         | Proxies validated messages delivered by the SSE server                                    |
| [asyncapi.sse.proxy](asyncapi.sse.proxy)                     | Streams messages published to a Kafka topic over SSE                                      |
| [grpc.echo](grpc.echo)                                       | Echoes messages sent to the gRPC server from a gRPC client                                |
| [grpc.kafka.echo](grpc.kafka.echo)                           | Echoes messages sent to a Kafka topic via gRPC from a gRPC client                         |
| [grpc.kafka.fanout](grpc.kafka.fanout)                       | Streams messages published to a Kafka topic, applying conflation based on log compaction  |
| [grpc.kafka.proxy](grpc.kafka.proxy)                         | Correlates gRPC requests and responses over separate Kafka topics                         |
| [grpc.proxy](grpc.proxy)                                     | Proxies gRPC requests and responses sent to the gRPC server from a gRPC client            |
| [http.filesystem](http.filesystem)                           | Serves files from a directory on the local filesystem                                     |
| [http.json.schema](http.json.schema)                         | Proxy request sent to the HTTP server from an HTTP client with schema enforcement         |
| [http.proxy.jwt](http.proxy.jwt)                             | Echoes request sent to the HTTP server from a JWT-authorized HTTP client                  |
| [http.kafka.async](http.kafka.async)                         | Correlates HTTP requests and responses over separate Kafka topics, asynchronously         |
| [http.kafka.avro.json](http.kafka.avro.json)                 | Validate messages while produce and fetch to a Kafka topic                                |
| [http.kafka.cache](http.kafka.cache)                         | Serves cached responses from a Kafka topic, detect when updated                           |
| [http.kafka.crud](http.kafka.crud)                           | Exposes a REST API with CRUD operations where a log-compacted Kafka topic acts as a table |
| [http.kafka.oneway](http.kafka.oneway)                       | Sends messages to a Kafka topic, fire-and-forget                                          |
| [http.kafka.proto.json](http.kafka.proto.json)               | Publish JSON over http and convert to a Protobuf serialized object onto a Kafka topic     |
| [http.kafka.proto.oneway](http.kafka.proto.oneway)           | Publish a Protobuf serialized object over HTTP onto a Kafka topic                         |
| [http.kafka.sync](http.kafka.sync)                           | Correlates HTTP requests and responses over separate Kafka topics                         |
| [http.proxy](http.proxy)                                     | Proxy request sent to the HTTP server from an HTTP client                                 |
| [mqtt.proxy.jwt](mqtt.proxy.jwt)                             | Proxies request sent to the MQTT server from a JWT-authorized MQTT client                 |
| [mqtt.kafka.broker](mqtt.kafka.proxy)                        | Forwards MQTT publish messages to Kafka, broadcasting to all subscribed MQTT clients      |
| [openapi.asyncapi.kakfa.proxy](openapi.asyncapi.kakfa.proxy) | Create an HTTP to Kafka REST proxy using OpenAPI and AsyncAPI schemas                     |
| [openapi.proxy](openapi.proxy)                               | Proxy requests defined in an OpenAPI schema sent to the HTTP server from an HTTP client   |
| [sse.jwt](sse.jwt)                                           | Proxies messages delivered by the SSE server, enforcing streaming security constraints    |
| [sse.kafka.fanout](sse.kafka.fanout)                         | Streams messages published to a Kafka topic, applying conflation based on log compaction  |
| [tcp.echo](tcp.echo)                                         | Echoes bytes sent to the TCP server                                                       |
| [tcp.reflect](tcp.reflect)                                   | Echoes bytes sent to the TCP server, broadcasting to all TCP clients                      |
| [tls.echo](tls.echo)                                         | Echoes encrypted bytes sent to the TLS server                                             |
| [tls.reflect](tls.reflect)                                   | Echoes encrypted bytes sent to the TLS server, broadcasting to all TLS clients            |
| [ws.echo](ws.echo)                                           | Echoes messages sent to the WebSocket server                                              |
| [ws.reflect](ws.reflect)                                     | Echoes messages sent to the WebSocket server, broadcasting to all WebSocket clients       |

Read the [docs][zilla-docs].
Try the [examples][zilla-examples].
Join the [Slack community][community-join].

[community-image]: https://img.shields.io/badge/slack-@aklivitycommunity-blue.svg?logo=slack
[community-join]: https://join.slack.com/t/aklivitycommunity/shared_invite/zt-sy06wvr9-u6cPmBNQplX5wVfd9l2oIQ
[Docker]: https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white
[Postman]: https://img.shields.io/badge/Postman-FF6C37?style=for-the-badge&logo=postman&logoColor=white
[zilla-docs]: https://docs.aklivity.io/zilla
[zilla-examples]: https://github.com/aklivity/zilla-examples
[docker-install]: https://docs.docker.com/compose/gettingstarted/
[postman-url]: https://www.postman.com/aklivity-zilla/
