# mqtt.kafka.reflect

Listens on mqtt port `1883` and will forward mqtt publish messages and proxies subscribes to mosquitto MQTT broker listening on `1884` for topic `smartylighting/streetlights/1/0/event/+/lighting/measured`.

Note: The `zilla.yaml` config used by the proxy was generated based on `asyncapi.yaml`.

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
- installs mosquitto MQTT broker to the Kubernetes cluster with helm and waits for the pod to start up
- starts port forwarding

```bash
$./setup.sh 
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install mqtt-proxy oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace mqtt-proxy --create-namespace --wait --values values.yaml --set-file 'zilla\.yaml=zilla.yaml'
Pulled: ghcr.io/aklivity/charts/zilla:0.9.46
Digest: sha256:4b0a63b076db9b53a9484889a11590b77f3184217c3d039973c532f25940adbc
NAME: mqtt-proxy
LAST DEPLOYED: [...]
NAMESPACE: mqtt-proxy
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
+ helm install zilla-mqtt-kafka-reflect-kafka chart --namespace zilla-mqtt-kafka-reflect --create-namespace --wait
NAME: zilla-mqtt-kafka-reflect-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-kafka-reflect
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ helm install mqtt-proxy-mosquitto chart --namespace mqtt-proxy --create-namespace --wait
NAME: mqtt-proxy-mosquitto
LAST DEPLOYED: Tue Sep 19 18:15:07 2023
NAMESPACE: mqtt-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ kubectl port-forward --namespace mqtt-proxy service/mqtt-proxy-zilla 1883
+ nc -z localhost 1883
+ kubectl port-forward --namespace mqtt-proxy service/mosquitto 1884
+ sleep 1
+ nc -z localhost 1883
Connection to localhost port 1883 [tcp/ibm-mqisdp] succeeded!
+ nc -z localhost 1884
Connection to localhost port 1884 [tcp/idmaps] succeeded!

```

### Install mqtt client

Requires MQTT 5.0 client, such as Mosquitto clients.

```bash
brew install mosquitto
```

### Verify behavior

Connect a subscribing client to Zilla to default port `1883`. Using mosquitto_pub client publish `Hello, world`  to the mosquitto broker on port `1884`. Verify that the message arrived to on the first client.

```bash
mosquitto_sub -V '5' -t 'smartylighting/streetlights/1/0/event/+/lighting/measured' -d
```

output:

```text
Client null sending CONNECT
Client 38c92d43-eac3-440d-b5b6-9ee64fb6b0ca received CONNACK (0)
Client 38c92d43-eac3-440d-b5b6-9ee64fb6b0ca sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 38c92d43-eac3-440d-b5b6-9ee64fb6b0ca received SUBACK
Subscribed (mid: 1): 0
Client 38c92d43-eac3-440d-b5b6-9ee64fb6b0ca received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```

```bash
mosquitto_pub -V '5' -t 'smartylighting/streetlights/1/0/event/0/lighting/measured' -m 'Hello, world' -d -p 1884
```

output:

```text
Client null sending CONNECT
Client auto-E62BEB8F-0F7F-42E5-001F-A25C2F715B0B received CONNACK (0)
Client auto-E62BEB8F-0F7F-42E5-001F-A25C2F715B0B sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client auto-E62BEB8F-0F7F-42E5-001F-A25C2F715B0B sending DISCONNECT
```

The other direction. Connect a subscribing client to mosquitto broker to port `1884`. Using mosquitto_pub client publish `Hello, world`  to Zilla on port `1883`. Verify that the message arrived to on the first client.
```bash
$ mosquitto_sub -V '5' -t 'smartylighting/streetlights/1/0/event/+/lighting/measured' -d -p 1884
Client null sending CONNECT
Client 42adb530-b483-4c73-9682-6fcc370ba871 received CONNACK (0)
Client 42adb530-b483-4c73-9682-6fcc370ba871 sending PUBLISH (d0, q0, r1, m1, 'zilla', ... (16 bytes))
Client 42adb530-b483-4c73-9682-6fcc370ba871 sending DISCONNECT
```

```bash
$ mosquitto_pub -V '5' -t 'smartylighting/streetlights/1/0/event/1/lighting/measured' -m 'Hello, world' -d
Client null sending CONNECT
Client auto-31F1B4CC-4F2A-27E0-ABCC-C00822CFA973 received CONNACK (0)
Client auto-31F1B4CC-4F2A-27E0-ABCC-C00822CFA973 sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client auto-31F1B4CC-4F2A-27E0-ABCC-C00822CFA973 received SUBACK
Subscribed (mid: 1): 0
Client auto-31F1B4CC-4F2A-27E0-ABCC-C00822CFA973 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and mosquitto broker and deletes the namespace.

```bash
./teardown.sh

```

output:

```text
+ + pgrep kubectl
99998
99999
+ killall kubectl
+ helm uninstall mqtt-proxy mqtt-proxy-mosquitto --namespace mqtt-proxy
release "mqtt-proxy" uninstalled
release "mqtt-proxy-mosquitto" uninstalled
+ kubectl delete namespace mqtt-proxy
namespace "mqtt-proxy" deleted
```
