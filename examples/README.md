# Zilla Examples

[![Slack Community][community-image]][community-join]

Try out these Zilla example configurations using `docker` in your local development enivronment.

| Name | Description|
|------|------------|
| [tcp.echo](tcp.echo) | Echoes bytes sent to the TCP server |
| [tcp.reflect](tcp.reflect) | Echoes bytes sent to the TCP server, broadcasting to all TCP clients |
| [tls.echo](tls.echo) | Echoes encrypted bytes sent to the TLS server |
| [tls.reflect](tls.reflect) | Echoes encrypted bytes sent to the TLS server, broadcasting to all TLS clients |
| [http.filesystem](http.filesystem) | Serves files from a directory on the local filesystem |
| [http.echo](http.echo) | Echoes request sent to the HTTP server from an HTTP client |
| [http.echo.jwt](http.echo.jwt) | Echoes request sent to the HTTP server from a JWT-authorized HTTP client |
| [http.kafka.sync](http.kafka.sync) | Correlates requests and responses over separate Kaka topics |
| [http.kafka.async](http.kafka.async) | Correlates requests and responses over separate Kaka topics, asynchronously |
| [http.kafka.cache](http.kafka.cache) | Serves cached responses from a Kaka topic |
| [amqp.reflect](amqp.reflect) | Echoes messages published to the AMQP server, broadcasting to all receiving AMQP clients |
| [mqtt.reflect](mqtt.reflect) | Echoes messages published to the MQTT server, broadcasting to all receiving MQTT clients |
| [sse.kafka.fanout](sse.kafka.fanout) | Streams messages published to a Kafka topic, applying conflation based on log compaction |
| [ws.echo](ws.echo) | Echoes messages sent to the WebSocket server |
| [ws.reflect](ws.reflect) | Echoes messages sent to the WebSocket server, broadcasting to all WebSocket clients |

Read the [docs][zilla-docs].
Try the [examples][zilla-examples].
Join the [Slack community][community-join].

[community-image]: https://img.shields.io/badge/slack-@aklivitycommunity-blue.svg?logo=slack
[community-join]: https://join.slack.com/t/aklivitycommunity/shared_invite/zt-sy06wvr9-u6cPmBNQplX5wVfd9l2oIQ

[zilla-docs]: https://docs.aklivity.io/zilla
[zilla-examples]: https://github.com/aklivity/zilla-examples
