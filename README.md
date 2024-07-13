<div id="top"></div>
<div align="center">
  <img src="./assets/zilla-rings@2x.png" height="250">
</div>

</br>

<div align="center"> 

  <!--[![Build Status][build-status-image]][build-status]-->
  [![Latest Release][release-latest-image]][release-latest]
  [![Slack Community][community-image]][community-join]
  [![Artifact HUB][artifact-hub-shield]][artifact-hub]
  
</div>

<h3 align="center">
  <a href="https://docs.aklivity.io/zilla/"><b>Docs</b></a> &bull;
  <a href="https://docs.aklivity.io/zilla/latest/how-tos/quickstart/"><b>Quickstart</b></a> &bull;
  <a href="https://github.com/aklivity/zilla-demos"><b>Demos</b></a> &bull;
  <a href="https://www.aklivity.io/blog"><b>Blog</b></a>  
</h3>

# 🦎 Zilla: a multi-protocol edge & service proxy

**Zilla** helps develop and manage event-driven applications and services. It is a lightweight yet feature-rich proxy with first-class support for `HTTP`, `SSE`, `gRPC`, `MQTT`, and `Kafka` protocols. 

Use **Zilla** as a:
- **Service sidecar** — deployed alongside a service to enforce authentication, validate schemas, gather metrics, and terminate TLS.
- **Kafka API gateway** — fronting Apache Kafka, enabling web apps, IoT clients, and non-Kafka microservices to directly consume and produce data streams via their own native protocols.

## Build real-time applications, integrate services, streamline architectures

| Zilla Use Case | Description |
| -------- | ------- |
| [Data broadcasting (Kafka fan-out) →](https://github.com/aklivity/zilla-examples/tree/main/sse.kafka.fanout) | Broadcast real-time updates to end users at web-scale. Power live data feeds such as stock tickers, betting odds, travel updates, and auction prices. |
| [IoT Kafka ingestion →](https://vordimous.github.io/zilla-docs/next/how-tos/mqtt/mqtt.kafka.broker.html) | Ingest and process IoT data in real-time by turning Kafka into a fully-fledged MQTT broker. |
| [Async workflows for sync microservices →](https://github.com/aklivity/zilla-examples/tree/main/http.kafka.async) | Make request-response microservice communication asynchronous by routing it over a pair of Kafka topics. |
| [Create an event-mesh →](https://www.aklivity.io/post/end-to-end-streaming-between-grpc-services-via-kafka) | Integrate mesh and event-driven microservices by routing connectivity through Kafka. Make Kafka look like a gRPC/REST server or gRPC client. |
| [Secure a Server Sent Event (SSE) API →](https://github.com/aklivity/zilla-examples/tree/main/sse.proxy.jwt) | Secure an SSE API by adding JWT-based Continous Authorization. |
| [Validate MQTT via AsyncAPI →](https://github.com/aklivity/zilla-examples/tree/main/mqtt.proxy.asyncapi) | Enforce an AsyncAPI schema for messages going into an MQTT broker. |
| **Much more!** | Check out all the [Zilla Demos](https://github.com/aklivity/zilla-demos) and [Zilla Examples](https://github.com/aklivity/zilla-examples). |

## Get Started

**Zilla** is stateless, declaratively configured, and has no external dependencies. The fastest way to get started is to follow the [Quickstart](https://docs.aklivity.io/zilla/latest/how-tos/quickstart/l).

## Installing Zilla

Single-node and cluster deployment options are available.
 
**Homebrew**
```
brew tap aklivity/tap 
brew install zilla

zilla start -ve -c ./zilla.yaml
```

**Docker**

```
docker pull ghcr.io/aklivity/zilla

docker run ghcr.io/aklivity/zilla:latest start -v
```

**Helm**

```
helm install zilla oci://ghcr.io/aklivity/charts/zilla --namespace zilla --create-namespace --wait \
--values values.yaml \
--set-file zilla\\.yaml=zilla.yaml
```

## Key integrations

- [x] Support for **OpenAPI** and **AsyncAPI** specifications for configuration and/or validation enforcement.
- [x] Integrations with external schema registries, such as **Apicurio** and **Karapace**, for a variety of data formats, including `JSON`, `avro`, and `protobuf`.
- [x] Support for authorization via `JWT`, including [continous authorization](https://www.aklivity.io/post/a-primer-on-server-sent-events-sse#:~:text=SSE%20is%20with-,Zilla%20Continuous%20Stream%20Authorization,-.) for `SSE`.
- [x] Integrations with standard observability tools, including **Prometheus** and **OpenTelemetry**, for logging and metrics.

## <a name="resources"> Resources

### 📚 Read the docs

- **[Zilla Documentation](https://docs.aklivity.io/zilla/latest):** Guides, tutorials and references to help understand how to use Zilla and configure it for your use case.
- **[Product Roadmap][zilla-roadmap]:** Check out our plan for upcoming releases. 
- **[Zilla Examples](https://github.com/aklivity/zilla-examples)**: A collection of pre-canned Zilla feature demos.
- **[Eventful Petstore Demo](https://github.com/aklivity/zilla-demos/tree/main/petstore):** See Zilla make the OpenAPI/Swagger Petstore service event-driven by mapping it onto Kafka in just a few lines of YAML.
- **[Taxi Demo](https://github.com/aklivity/zilla-demos/tree/main/petstore):** A demo of a taxi-based IoT deployment with Zilla, Kafka, OpenAPIs and AsyncAPIs.

### 📝 Check out blog posts

- **[Bring your own REST APIs for Apache Kafka](https://www.aklivity.io/post/bring-your-own-rest-apis-for-apache-kafka):** Zilla enables application-specific REST APIs. See how it's not just another Kafka-REST proxy.
- **[Modern Eventing with CQRS, Redpanda and Zilla](https://www.aklivity.io/post/modern-eventing-with-cqrs-redpanda-and-zilla):** Learn about the event-driven nature of CQRS, common challenges while implementing it, and how Zilla solves them with Redpanda.
- **[End-to-end Streaming Between gRPC Services via Kafka](https://www.aklivity.io/post/end-to-end-streaming-between-grpc-services-via-kafka):** Learn how to integrate gRPC with Kafka event streaming; securely, reliably and scalably.
- **[Zilla Hails a Taxi](https://www.aklivity.io/post/zilla-hails-a-taxi):** IoT telemetry at scale? MQTT, Zilla, and Kafka can make it happen.

### <a name="support"> ❓ Get support

- **[Community Slack](https://www.aklivity.io/slack):** Join technical discussions, ask questions, and meet other users!
- **[GitHub Issues](https://github.com/aklivity/zilla/issues):** Report bugs or issues with Zilla.
- **[Contact Us](https://www.aklivity.io/contact):** Submit non-techinal questions and inquiries.

## <a name="community"> 🌱 Community

Looking to contribute to Zilla? Check out the [Contributing to Zilla](./.github/CONTRIBUTING.md) guide.
✨We value all contributions, whether source code, documentation, bug reports, feature requests or feedback!

### Many Thanks To Our Contributors!

<a href="https://github.com/aklivity/zilla/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=aklivity/zilla" />
</a>

## <a name="license"> License

Zilla is made available under the [Aklivity Community License](./LICENSE-AklivityCommunity). This is an open source-derived license that gives you the freedom to deploy, modify and run Zilla as you see fit, as long as you are not turning into a standalone commercialized “Zilla-as-a-service” offering. Running Zilla in the cloud for your own workloads, production or not, is completely fine.


<!-- Links -->
[build-status-image]: https://github.com/aklivity/zilla/workflows/build/badge.svg
[build-status]: https://github.com/aklivity/zilla/actions
[community-image]: https://img.shields.io/badge/slack-@aklivitycommunity-blue.svg?logo=slack
[community-join]: https://www.aklivity.io/slack
[artifact-hub-shield]: https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/zilla
[artifact-hub]: https://artifacthub.io/packages/helm/zilla/zilla
[release-latest-image]: https://img.shields.io/github/v/tag/aklivity/zilla?label=release
[release-latest]: https://github.com/aklivity/zilla/pkgs/container/zilla
[zilla-roadmap]: https://github.com/orgs/aklivity/projects/4/views/1

<p align="right">(<a href="#top">🔼 Back to top</a>)</p>
