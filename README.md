<div align="center">
  <img src="./assets/logo-light-mode.svg#gh-light-mode-only" height="100">
  <img src="./assets/logo-dark-mode.svg#gh-dark-mode-only" height="100">
</div>

</br>
<h1 align="center">Event-driven API Gateway</h1>

<div align="center">
  
  [![Build Status][build-status-image]][build-status]
  [![Slack Community][community-image]][community-join]
  
</div>

<h3 align="center">
  <a href="https://docs.aklivity.io/zilla/"><b>Documentation</b></a> &bull;
  <a href="https://docs.aklivity.io/zilla/get-started"><b>Getting Started</b></a> &bull;
  <a href="https://github.com/aklivity/zilla-examples"><b>Examples</b></a> &bull; 
  <a href="https://www.aklivity.io/blog"><b>Blog</b></a>  
</h3>

## About Zilla
Zilla is a next-generation API gateway built for event-driven architectures and streaming. It is the most seamless and reliable way of interfacing edge clients (mobile apps, browsers, partner systems, etc.) to Apache Kafka-based microservices and data.

With Zilla, apps and services can use standard protocols such as HTTP, SSE and the native Kafka protocol (see roadmap for additional protocols on the way) to directly consume and produce Kafka event-streams over the internet. 

<b>Zilla aims to:</b>

1. Streamline event-driven architectures and make them easier to manage by eliminating the need for intermediary brokers and web servers, sink/source connectors, and change data capture (CDC) tooling.
2. Simplify creating scalable, asynchronous backends that can support realtime frontend experiences.

## Features

### Kafka Proxies
Zilla natively supports the Kafka protocol and is able to efficiently transform other protocols to and from it. Connectivity to Kafka brokers over PLAINTEXT, TLS/SSL and TLS/SSL with Client Certificates are supported. 

- <b><a href="https://docs.aklivity.io/zilla/reference/zilla.json/binding-http-kafka">HTTP ⇄ Kafka</a></b> — 
  Transforms HTTP 1.1/HTTP 2  requests and responses to Kafka topic streams with control over the topic, message key, message headers, message value and reply-to topic. JWT authentication supported.
- <b><a href="https://docs.aklivity.io/zilla/reference/zilla.json/binding-http-kafka">SSE ← Kafka</a></b> — 
  Transforms Kafka topic streams to Server Sent Event (SSE) streams for reliable message down to web clients. Secured via JWTs and Zilla’s continuous authentication  mechanism, which re-authorize clients transparently without abruptly terminating their message streams.

### Other
- <b>CORS</b> — enable CORS so users can make browser based requests to Zilla APIs.
- <b>Entitlement-based Messaging</b> — restrict access to endpoints based on client entitlement privileges.
- <b>SSL/TLS</b> — support for the latest version of TLS.











<p align="center">
  <img src="./assets/diagram-light-mode.svg#gh-light-mode-only" >
  <img src="./assets/diagram-dark-mode.svg#gh-dark-mode-only" >
</p>

<!-- left aligned
![Zilla diagram](./assets/diagram-dark-mode.svg#gh-dark-mode-only)
![Zilla diagram](./assets/diagram-light-mode.svg#gh-light-mode-only)
-->

Zilla is designed on the fundamental principle that _every data flow is a stream_, and that streams can be composed together to create efficient protocol transformation pipelines. This concept of a stream holds at both the network level for communication protocols and also at the application level for data processing.

Zilla's declarative configuration defines a routed graph of protocol decoders, transformers, encoders and caches that combine to provide a secure and stateless API entry point to your event-driven architecture.

For example, when deployed in front of a Kafka cluster, Zilla can be configured to support:
- HTTP request-response interaction with Kafka-based microservices
- HTTP event-driven caching populated by messages from a Kafka topic
- reliable message streaming from a Kafka topic via Server-Sent Events
- secure HTTP request-response APIs using JWT access tokens
- secure Server-Sent Events streams using continuous authorization via JWT access tokens

As a developer, you can focus on writing and testing your event-driven microservices with technologies such as Kafka consumers and producers, you can define your web and mobile APIs using Zilla, and then you can deploy securely at global scale.

Read the [docs][zilla-docs].
Try the [examples][zilla-examples].
Join the [Slack community][community-join].

## Running Zilla via docker
Run the latest Zilla release with default empty configuration via docker.
```
docker run ghcr.io/aklivity/zilla:latest start -v
```
```
{
  "name": "default"
}
started
```

Configure Zilla to behave as a `tcp` `echo` server in 2mins.

First create a local `zilla.json` with the following contents.
```json
{
    "name": "example",
    "bindings":
    {
        "tcp_server0":
        {
            "type" : "tcp",
            "kind": "server",
            "options":
            {
                "host": "0.0.0.0",
                "port": 12345
            },
            "exit": "echo_server0"
        },
        "echo_server0":
        {
            "type" : "echo",
            "kind": "server"
        }
    }
}

```
Then run Zilla again, this time mounting your local `zilla.json` as a docker volume file.
```
docker run -v `pwd`/zilla.json:/zilla.json ghcr.io/aklivity/zilla:latest start -v
```
Now, try it out using `netcat`.
```bash
nc localhost 12345
```
```
Hello, world
Hello, world
```

Check out the [docs][zilla-docs] and [examples][zilla-examples] to learn how to configure Zilla. Follow the [tutorial][zilla-todo-tutorial] to build a CQRS Todo app with Zilla and Kafka Streams. Ask questions in the [Slack community][community-join].

## Roadmap

Zilla is designed from the ground up to be extensible and we anticipate adding support for several new capabilities:
 - gRPC, proxy and Kafka mapping
 - GraphQL, proxy and Kafka mapping
 - MQTT, proxy and Kafka mapping
 - AMQP, proxy and Kafka mapping
 - WebSocket, proxy and Kafka mapping
 - WebHooks, Kafka mapping
 - HTTP, proxy, including HTTP/3
 - SSE, proxy (Kafka mapping done)
 - OpenAPI integration
 - AsyncAPI integration
 - Avro integration
 - OpenTelemetry integration

Please let us know in the [Slack community][community-join] if you have additional suggestions.

## Build from source
```bash
./mvnw clean install
```
This creates a local `docker` image with version `develop-SNAPSHOT`.


## License

The project is licensed under the [Aklivity Community License](LICENSE-AklivityCommunity), except for selected components
which are under the [Apache 2.0 license](LICENSE-Apache).
See `LICENSE` file in each subfolder for detailed license agreement.

[build-status-image]: https://github.com/aklivity/zilla/workflows/build/badge.svg
[build-status]: https://github.com/aklivity/zilla/actions

[community-image]: https://img.shields.io/badge/slack-@aklivitycommunity-blue.svg?logo=slack
[community-join]: https://join.slack.com/t/aklivitycommunity/shared_invite/zt-sy06wvr9-u6cPmBNQplX5wVfd9l2oIQ

[zilla-docs]: https://docs.aklivity.io/zilla
[zilla-examples]: https://github.com/aklivity/zilla-examples
[zilla-todo-tutorial]: https://docs.aklivity.io/zilla/get-started/build-todo-app
