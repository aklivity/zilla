# mqtt.kafka.broker

This is the resource folder for the running the [MQTT Kafka broker guide](https://docs.aklivity.io/zilla/latest/how-tos/mqtt/mqtt.kafka.broker.html) found on our docs.

## Running locally

This example can be run using Docker compose or Kubernetes. The setup scripts are in the [compose](./docker/compose) and [helm](./k8s/helm) folders respectively and work the same way.

You will need a running kafka broker. To start one locally you will find instructions in the [kafka.broker](../kafka.broker) folder.

### Setup

Wether you chose [compose](./docker/compose) or [helm](./k8s/helm), the `setup.sh` script will:

- create the necessary kafka topics
- create an MQTT broker at `http://localhost:7183`

```bash
./setup.sh
```

### Using this example

Follow the steps on our [MQTT Kafka broker guide](https://docs.aklivity.io/zilla/latest/how-tos/mqtt/mqtt.kafka.broker.html#send-a-greeting)

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
