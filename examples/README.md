# Zilla Examples

[![Slack Community][community-image]][community-join]

Try out these Zilla example configurations using Kubernetes.

If you are running Docker Desktop, enable Kubernetes, and it will start up a one-node local Kubernetes cluster.
It puts `kubectl` to `/usr/local/bin` and it creates the appropriate `~/.kube/config` file for you.
You can install `helm` with `$ brew install helm`.

| Name                                                                | Description                                                                                  |
|---------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| [tcp.echo](tcp.echo)                                                | Echoes bytes sent to the TCP server                                                          |
| [tcp.reflect](tcp.reflect)                                          | Echoes bytes sent to the TCP server, broadcasting to all TCP clients                         |
| [tls.echo](tls.echo)                                                | Echoes encrypted bytes sent to the TLS server                                                |
| [tls.reflect](tls.reflect)                                          | Echoes encrypted bytes sent to the TLS server, broadcasting to all TLS clients               |
| [http.filesystem](http.filesystem)                                  | Serves files from a directory on the local filesystem                                        |
| [http.filesystem.config.server](http.filesystem.config.server)      | Serves files from a directory on the local filesystem, getting the config from a http server |
| [http.echo](http.echo)                                              | Echoes request sent to the HTTP server from an HTTP client                                   |
| [http.echo.jwt](http.echo.jwt)                                      | Echoes request sent to the HTTP server from a JWT-authorized HTTP client                     |
| [http.proxy](http.proxy)                                            | Proxy request sent to the HTTP server from an HTTP client                                    |
| [http.kafka.sync](http.kafka.sync)                                  | Correlates HTTP requests and responses over separate Kafka topics                            |
| [http.kafka.async](http.kafka.async)                                | Correlates HTTP requests and responses over separate Kafka topics, asynchronously            |
| [http.kafka.cache](http.kafka.cache)                                | Serves cached responses from a Kafka topic, detect when updated                              |
| [http.kafka.oneway](http.kafka.oneway)                              | Sends messages to a Kafka topic, fire-and-forget                                             |
| [http.kafka.crud](http.kafka.crud)                                  | Exposes a REST API with CRUD operations where a log-compacted Kafka topic acts as a table    |
| [http.kafka.sasl.scram](http.kafka.sasl.scram)                      | Sends messages to a SASL/SCRAM enabled Kafka                                                 |
| [http.redpanda.sasl.scram](http.redpanda.sasl.scram)                | Sends messages to a SASL/SCRAM enabled Redpanda Cluster                                      |
| [kubernetes.prometheus.autoscale](kubernetes.prometheus.autoscale)  | Demo Kubernetes HorizonalPodAutoscaling feature based a on a custom metric with Prometheus   |
| [grpc.echo](grpc.echo)                                              | Echoes messages sent to the gRPC server from a gRPC client                                   |
| [grpc.kafka.echo](grpc.kafka.echo)                                  | Echoes messages sent to a Kafka topic via gRPC from a gRPC client                            |
| [grpc.kafka.fanout](grpc.kafka.fanout)                              | Streams messages published to a Kafka topic, applying conflation based on log compaction     |
| [grpc.kafka.proxy](grpc.kafka.proxy)                                | Correlates gRPC requests and responses over separate Kafka topics                            |
| [grpc.proxy](grpc.proxy)                                            | Proxies gRPC requests and responses sent to the gRPC server from a gRPC client               |
| [amqp.reflect](amqp.reflect)                                        | Echoes messages published to the AMQP server, broadcasting to all receiving AMQP clients     |
| [mqtt.kafka.reflect](mqtt.kafka.reflect)                            | Forwards MQTT publish messages to Kafka, broadcasting to all subscribed MQTT clients         |
| [sse.kafka.fanout](sse.kafka.fanout)                                | Streams messages published to a Kafka topic, applying conflation based on log compaction     |
| [sse.proxy.jwt](sse.proxy.jwt)                                      | Proxies messages delivered by the SSE server, enforcing streaming security constraints       |
| [ws.echo](ws.echo)                                                  | Echoes messages sent to the WebSocket server                                                 |
| [ws.reflect](ws.reflect)                                            | Echoes messages sent to the WebSocket server, broadcasting to all WebSocket clients          |

Read the [docs][zilla-docs].
Try the [examples][zilla-examples].
Join the [Slack community][community-join].

[community-image]: https://img.shields.io/badge/slack-@aklivitycommunity-blue.svg?logo=slack
[community-join]: https://join.slack.com/t/aklivitycommunity/shared_invite/zt-sy06wvr9-u6cPmBNQplX5wVfd9l2oIQ

[zilla-docs]: https://docs.aklivity.io/zilla
[zilla-examples]: https://github.com/aklivity/zilla-examples
