# quickstart

Follow the [Zilla Quickstart](https://docs.aklivity.io/zilla/latest/tutorials/quickstart/kafka-proxies.html) to discover some of what Zilla can do!

## Running locally

This quickstart runs using Docker compose. You will find the setup scripts in the [compose](./docker/compose) folder.

You will need a running kafka broker. To start one locally you will find instructions in the [kafka.broker](../kafka.broker) folder.

### Setup

The `setup.sh` script will:

- Configured Zilla instance with REST, SSE, gRPC, MQTT protocols configured
- Create Kafka topics
- Start a gRPC Route Guide server
- Start a MQTT message simulator

```bash
./compose/setup.sh
```

### Using this quickstart

You can interact with this quickstart using our [Postman collection](https://vordimous.github.io/zilla-docs/next/tutorials/quickstart/kafka-proxies.html#postman-collections)

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./compose/teardown.sh
```
