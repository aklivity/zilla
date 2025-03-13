# asyncapi.mqtt.kafka.proxy

In this guide, you create Kafka topics and use Zilla to mediate MQTT broker messages onto those topics.
Zilla implements MQTT API defined in AsyncAPI specifications and uses Kafka API defined AsyncAPI proxy MQTT messages to Kafka.

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Using this example

Using eclipse-mosquitto subscribe to the `smartylighting/streetlights/1/0/event/+/lighting/measured` topic.

```bash
docker compose -p zilla-asyncapi-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_sub --url mqtt://zilla.examples.dev:7183/smartylighting/streetlights/1/0/event/+/lighting/measured --debug
```

output:

```text
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
docker compose -p zilla-asyncapi-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_pub --url mqtt://zilla.examples.dev:7183/smartylighting/streetlights/1/0/event/1/lighting/measured --message '{"lumens":50,"sentAt":"2024-06-07T12:34:32.000Z"}' --debug
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
docker compose -p zilla-asyncapi-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_pub --url mqtt://zilla.examples.dev:7183/smartylighting/streetlights/1/0/event/1/lighting/measured -m '{"lumens":-1,"sentAt":"2024-06-07T12:34:32.000Z"}' --repeat 2 --repeat-delay 3 --debug
```

output:

```
Client null sending CONNECT
Client 30157eed-0ea7-42c6-91e8-466d1dd0ab66 received CONNACK (0)
Client 30157eed-0ea7-42c6-91e8-466d1dd0ab66 sending PUBLISH (d0, q0, r0, m1, 'smartylighting/streetlights/1/0/event/1/lighting/measured', ... (49 bytes))
Received DISCONNECT (153)
Error: The client is not currently connected.
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```

