# mqtt.kafka.proxy

In this guide, you create Kafka topics and use Zilla to mediate MQTT broker messages onto those topics.

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Using this example

Using `mosquitto-cli` subscribe to the `zilla` topic.

```bash
docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_sub --url mqtt://zilla.examples.dev:7183/zilla --debug
```

output:

```text
Client null sending CONNECT
Client null received CONNACK (0)
Client null sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client null received SUBACK
Subscribed (mid: 1): 0
Client null received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello Zilla!
```

In a separate session, publish a valid message on the `zilla` topic.

```bash
docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_pub --url mqtt://zilla.examples.dev:7183/zilla --message 'Hello Zilla!' --debug
```

output:

```
Client null sending CONNECT
Client null received CONNACK (0)
Client null sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client null sending DISCONNECT
```

### Shared subscriptions

MQTT 5 shared subscriptions deliver each matching message to exactly one of the sessions subscribed with the same `$share/<ShareName>/<filter>`. Zilla maps a shared subscription to a Kafka share group, so the Kafka broker must have share groups enabled and the `mqtt-kafka` binding needs a `store` to keep the subscription lifecycle and the claim tickets of QoS 2 messages.

The [compose.yaml](compose.yaml) enables share groups on the single node Kafka broker with the `share.version` feature, enabled by the `kafka-init` service, and a replication factor of 1 for the share coordinator state topic. The [zilla.yaml](etc/zilla.yaml) configures the `memory0` store on the `mqtt-kafka` binding.

Using `mosquitto-cli` start two subscribers to the same shared subscription.

```bash
docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_sub -V 5 -q 1 --url 'mqtt://zilla.examples.dev:7183/$share/group/sensor/#' --debug
```

In a separate session, publish messages on the `sensor/temperature` topic.

```bash
docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_pub -V 5 -q 1 --url mqtt://zilla.examples.dev:7183/sensor/temperature --message '21.5'
```

Each message is received by only one of the subscribers. The share group is listed by the Kafka broker.

```bash
docker compose -p zilla-mqtt-kafka-proxy exec -T kafka \
    /opt/kafka/bin/kafka-share-groups.sh --bootstrap-server kafka.examples.dev:29092 --list
```

output:

```text
zilla.zilla-mqtt-kafka-proxy-north_mqtt_kafka_mapping.share.group.c2Vuc29yLyM.0
```

The group is named after the binding, the share name, the base64url encoded filter and an epoch. A later subscriber starts a new group, with a new epoch, after every session has left the shared subscription, so it does not receive the messages published in the meantime.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```

