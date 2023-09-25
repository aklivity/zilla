# mqtt.kafka.reflect

Listens on mqtt port `1883` and will forward mqtt publish messages to Kafka, delivering to all mqtt clients subscribed to the same topic.
Listens on mqtts port `8883` and will forward mqtt publish messages to Kafka, delivering to all mqtt clients subscribed to the same topic.

### Requirements

- bash, jq, nc
- Zilla docker image local incubator build, `develop-SNAPSHOT` version
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- mosquitto
- kcat

### Setup

The `setup.sh` script:

- installs Zilla to the Kubernetes cluster with helm and waits for the pod to start up
- starts port forwarding

```bash
$./setup.sh   
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-mqtt-kafka-broker oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-mqtt-kafka-broker --create-namespace --wait [...]
NAME: zilla-mqtt-kafka-broker
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-kafka-broker
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
+ helm install zilla-mqtt-kafka-broker-kafka chart --namespace zilla-mqtt-kafka-broker --create-namespace --wait
NAME: zilla-mqtt-kafka-broker-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-kafka-broker
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-mqtt-kafka-broker --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-74675fbb8-g56l9
+ kubectl exec --namespace zilla-mqtt-kafka-broker pod/kafka-74675fbb8-g56l9 -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic mqtt-messages --if-not-exists
Created topic mqtt-messages.
+ kubectl exec --namespace zilla-mqtt-kafka-broker pod/kafka-74675fbb8-w42xt -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic mqtt-retained --config cleanup.policy=compact --if-not-exists
Created topic mqtt-retained.
+ kubectl exec --namespace zilla-mqtt-kafka-broker pod/kafka-74675fbb8-w42xt -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic mqtt-sessions --config cleanup.policy=compact --if-not-exists
Created topic mqtt-sessions.
+ kubectl port-forward --namespace zilla-mqtt-kafka-broker service/zilla-mqtt-kafka-broker 1883 8883
+ nc -z localhost 1883
+ kubectl port-forward --namespace zilla-mqtt-kafka-broker service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 1883
Connection to localhost port 1883 [tcp/ibm-mqisdp] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Install mqtt client

Requires MQTT 5.0 client, such as Mosquitto clients.

```bash
brew install mosquitto
```

### Install kcat client

Requires Kafka client, such as `kcat`.

```bash
brew install kcat
```

### Verify behavior

Connect two subscribing clients first, then send `Hello, world` from publishing client. Verify that the message arrived to Kafka.

```bash
mosquitto_sub -V '5' -t 'zilla' -d
```

output:

```text
Client null sending CONNECT
Client 2b77314a-163f-4f18-908c-2913645e4f56 received CONNACK (0)
Client 2b77314a-163f-4f18-908c-2913645e4f56 sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 2b77314a-163f-4f18-908c-2913645e4f56 received SUBACK
Subscribed (mid: 1): 0
Client 2b77314a-163f-4f18-908c-2913645e4f56 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```

```bash
mosquitto_sub -V '5' -t 'zilla' --cafile test-ca.crt -d
```

output:

```text
Client null sending CONNECT
Client 26ab67d8-4a61-4e14-9d95-6a383c0cbdd7 received CONNACK (0)
Client 26ab67d8-4a61-4e14-9d95-6a383c0cbdd7 sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 26ab67d8-4a61-4e14-9d95-6a383c0cbdd7 received SUBACK
Subscribed (mid: 1): 0
Client 26ab67d8-4a61-4e14-9d95-6a383c0cbdd7 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```

```bash
mosquitto_pub -V '5' -t 'zilla' -m 'Hello, world' -d
```

output:

```text
Client null sending CONNECT
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 received CONNACK (0)
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 sending DISCONNECT
```

Check the internal mqtt-messages topic in Kafka
```bash
kcat -C -b localhost:9092 -t mqtt-messages -J -u | jq .
{
  "topic": "mqtt-messages",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1683710037377,
  "broker": 1,
  "headers": [
    "zilla:topic",
    "zilla",
    "zilla:local",
    "44181407-f1bc-4a6b-b94d-9f37d37ea395",
    "zilla:format",
    "BINARY"
  ],
  "key": "zilla",
  "payload": "Hello, world"
}
```

output:

```text
% Reached end of topic mqtt-messages [0] at offset 1
```

Verify retained messages
```bash
$ mosquitto_pub -V '5' -t 'zilla' -m 'Retained message' -d --retain
Client null sending CONNECT
Client 42adb530-b483-4c73-9682-6fcc370ba871 received CONNACK (0)
Client 42adb530-b483-4c73-9682-6fcc370ba871 sending PUBLISH (d0, q0, r1, m1, 'zilla', ... (16 bytes))
Client 42adb530-b483-4c73-9682-6fcc370ba871 sending DISCONNECT
```

```bash
$ mosquitto_pub -V '5' -t 'zilla' -m 'Retained message - latest' -d --retain
Client null sending CONNECT
Client 517dc705-1f01-4fbc-af01-2595fcd7d78b received CONNACK (0)
Client 517dc705-1f01-4fbc-af01-2595fcd7d78b sending PUBLISH (d0, q0, r1, m1, 'zilla', ... (25 bytes))
Client 517dc705-1f01-4fbc-af01-2595fcd7d78b sending DISCONNECT
```

```bash
$ mosquitto_pub -V '5' -t 'zilla' -m 'Non-retained message' -d
Client null sending CONNECT
Client 0e383f18-65ca-45fd-9438-b17d4659686d received CONNACK (0)
Client 0e383f18-65ca-45fd-9438-b17d4659686d sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (20 bytes))
Client 0e383f18-65ca-45fd-9438-b17d4659686d sending DISCONNECT
```

```bash
$ mosquitto_sub -V '5' -t 'zilla' -d
Client null sending CONNECT
Client cf8c4c46-77e0-4086-910e-33d3ba54ad76 received CONNACK (0)
Client cf8c4c46-77e0-4086-910e-33d3ba54ad76 sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client cf8c4c46-77e0-4086-910e-33d3ba54ad76 received SUBACK
Subscribed (mid: 1): 0
Client cf8c4c46-77e0-4086-910e-33d3ba54ad76 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (25 bytes))
Retained message - latest
```

Only the latest retained message was delivered, and the non-retained message was not.

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
./teardown.sh

```

output:

```text
+ pgrep kubectl
99998
99999
+ killall kubectl
+ helm uninstall zilla-mqtt-kafka-broker zilla-mqtt-kafka-broker-kafka --namespace zilla-mqtt-kafka
release "zilla-mqtt-kafka-broker" uninstalled
release "zilla-mqtt-kafka-broker-kafka" uninstalled
+ kubectl delete namespace zilla-mqtt-kafka
namespace "zilla-mqtt-kafka" deleted
```
