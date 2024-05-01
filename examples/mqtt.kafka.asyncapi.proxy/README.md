# mqtt.kafka.asyncapi.proxy

In this guide, you create Kafka topics and use Zilla to mediate MQTT broker messages onto those topics.
Zilla implements MQTT API defined in AsyncAPI specifications and uses Kafka API defined AsyncAPI proxy MQTT messages to Kafka.

## Running locally

This example can be run using Docker compose or Kubernetes. The setup scripts are in the [compose](./docker/compose) and [helm](./k8s/helm) folders respectively and work the same way.

You will need a running kafka broker. To start one locally you will find instructions in the [kafka.broker](../kafka.broker) folder. Alternatively you can use the [redpanda.broker](../redpanda.broker) folder.

### Setup

Wether you chose [compose](./docker/compose) or [helm](./k8s/helm), the `setup.sh` script will:

- create the necessary kafka topics
- create an MQTT broker at `mqtt://localhost:7183`

```bash
./setup.sh
```

### Using this example

Using eclipse-mosquitto subscribe to the `smartylighting/streetlights/1/0/event/+/lighting/measured` topic.

```bash
 mosquitto_sub -V '5' -t 'smartylighting/streetlights/1/0/event/+/lighting/measured' -d -p 7183
```

output:

```
Client null sending CONNECT
Client 26c02b9a-0e29-44c6-9f0e-277655c8d712 received CONNACK (0)
Client 26c02b9a-0e29-44c6-9f0e-277655c8d712 sending SUBSCRIBE (Mid: 1, Topic: smartylighting/streetlights/1/0/event/+/lighting/measured, QoS: 0, Options: 0x00)
Client 26c02b9a-0e29-44c6-9f0e-277655c8d712 received SUBACK
Subscribed (mid: 1): 0
Client 26c02b9a-0e29-44c6-9f0e-277655c8d712 received PUBLISH (d0, q0, r0, m0, 'smartylighting/streetlights/1/0/event/5/lighting/measured', ... (49 bytes))
{"lumens":50,"sentAt":"2024-06-07T12:34:32.000Z"}
```

In a separate session, publish a valid message on the `smartylighting/streetlights/1/0/event/1/lighting/measured` topic.

```bash
mosquitto_pub -V '5' -t 'smartylighting/streetlights/1/0/event/1/lighting/measured' -m '{"lumens":50,"sentAt":"2024-06-07T12:34:32.000Z"}' -d -p 7183
```

output:

```
Client null sending CONNECT
Client a1f4ad8c-c9e8-4671-ad46-69030d4f1c9a received CONNACK (0)
Client a1f4ad8c-c9e8-4671-ad46-69030d4f1c9a sending PUBLISH (d0, q0, r0, m1, 'smartylighting/streetlights/1/0/event/1/lighting/measured', ... (49 bytes))
Client a1f4ad8c-c9e8-4671-ad46-69030d4f1c9a sending DISCONNECT
```

Now attempt to publish an invalid message by setting `lumens` property to a negative value.

```bash
mosquitto_pub -V '5' -t 'smartylighting/streetlights/1/0/event/1/lighting/measured' -m '{"lumens":-1,"sentAt":"2024-06-07T12:34:32.000Z"}' -d -p 7183 --repeat 2 --repeat-delay 3
```

output:
```
Client null sending CONNECT
Client 30157eed-0ea7-42c6-91e8-466d1dd0ab66 received CONNACK (0)
Client 30157eed-0ea7-42c6-91e8-466d1dd0ab66 sending PUBLISH (d0, q0, r0, m1, 'smartylighting/streetlights/1/0/event/1/lighting/measured', ... (49 bytes))
Received DISCONNECT (153)
Error: The client is not currently connected.
```

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
