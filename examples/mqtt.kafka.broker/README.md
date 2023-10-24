# mqtt.kafka.broker

This is the resource folder for the running the [MQTT Kafka broker guide](https://docs.aklivity.io/zilla/next/how-tos/mqtt/mqtt.kafka.broker.html) found on our docs.

## Running locally

This example can be run using Docker compose or Kubernetes. The setup scripts are in the [compose](./compose) and [k8s](./k8s/) folders respectively and work the same way.

You will need a running kafka broker. To start one locally you will find instructions in the [kafka.broker](../kafka.broker) folder. You will need to export the below environment variables before running the setup script.

```bash
export KAFKA_HOST=host.docker.internal
export KAFKA_PORT=29092
```

### Setup

Wether you chose [compose](./compose) or [k8s](./k8s/), the `setup.sh` script will:

- create the necessary kafka topics
- create an MQTT broker at `http://localhost:7183`

```bash
./setup.sh
```

### Using this example

Follow the steps on our [MQTT Kafka broker guide](https://docs.aklivity.io/zilla/next/how-tos/mqtt/mqtt.kafka.broker.html#send-a-greeting)

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
