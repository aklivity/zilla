# mqtt.kafka.asyncapi.proxy

In this guide, you create Kafka topics and use Zilla to mediate MQTT broker messages onto those topics.
Zilla implements MQTT API defined in AsyncAPI specifications and uses Kafka API defined AsyncAPI proxy MQTT messages to Kafka.

## Running locally

This example can be run using Docker compose or Kubernetes. The setup scripts are in the [compose](./docker/compose) and [helm](./k8s/helm) folders respectively and work the same way.

You will need a running kafka broker. To start one locally you will find instructions in the [kafka.broker](../kafka.broker) folder.

### Setup

Wether you chose [compose](./docker/compose) or [helm](./k8s/helm), the `setup.sh` script will:

- create the necessary kafka topics
- create an MQTT broker at `mqtt://localhost:7183`

```bash
./setup.sh
```

### Using this example

Using eclipse-mosquitto subscribe to the sensors/# topic.

```bash
mosquitto_sub -V '5' -t 'sensors/#' -d -p 7183
```

output:

```
Client null sending CONNECT
Client 7f37e11e-8f79-458a-a4b3-3ffb36a9e08b received CONNACK (0)
Client 7f37e11e-8f79-458a-a4b3-3ffb36a9e08b sending SUBSCRIBE (Mid: 1, Topic: sensors/#, QoS: 0, Options: 0x00)
Client 7f37e11e-8f79-458a-a4b3-3ffb36a9e08b received SUBACK
Subscribed (mid: 1): 0
Client 7f37e11e-8f79-458a-a4b3-3ffb36a9e08b received PUBLISH (d0, q0, r0, m0, 'sensors/1', ... (24 bytes))
{"id":"1","status":"on"}
```

In a separate session, publish a valid message on the sensors/1 topic.

```bash
mosquitto_pub -V '5' -t 'sensors/1' -m '{"id":"1","status":"on"}' -d -p 7183
```

output:

```
Client null sending CONNECT
Client 2fc05cdc-5e1d-4e00-be18-0026ae47e749 received CONNACK (0)
Client 2fc05cdc-5e1d-4e00-be18-0026ae47e749 sending PUBLISH (d0, q0, r0, m1, 'sensors/1', ... (24 bytes))
Client 2fc05cdc-5e1d-4e00-be18-0026ae47e749 sending DISCONNECT
```

Now attempt to publish an invalid message, with property `stat` instead of `status`.

```bash
mosquitto_pub -V '5' -t 'sensors/1' -m '{"id":"1","stat":"off"}' -d -p 7183 --repeat 2 --repeat-delay 3
```

output:

```
Client null sending CONNECT
Client cd166c27-de75-4a2e-b3c7-f16631bda2a9 received CONNACK (0)
Client cd166c27-de75-4a2e-b3c7-f16631bda2a9 sending PUBLISH (d0, q0, r0, m1, 'sensors/1', ... (23 bytes))
Received DISCONNECT (153)
Error: The client is not currently connected.
```

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
