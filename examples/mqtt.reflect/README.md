# mqtt.reflect (incubator)

Listens on mqtt port `1883` and will echo back whatever is published to the server, broadcasting to all subscribed clients.
Listens on mqtts port `8883` and will echo back whatever is published to the server, broadcasting to all subscribed clients.

### Requirements

- bash, jq, nc
- Zilla docker image local incubator build, `develop-SNAPSHOT` version
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- mosquitto

### Setup

The `setup.sh` script:
- installs Zilla to the Kubernetes cluster with helm and waits for the pod to start up
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-mqtt-reflect chart --namespace zilla-mqtt-reflect --create-namespace --wait
NAME: zilla-mqtt-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-reflect
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ nc -z localhost 1883
+ kubectl port-forward --namespace zilla-mqtt-reflect service/zilla 1883 8883
+ sleep 1
+ nc -z localhost 1883
Connection to localhost port 1883 [tcp/ibm-mqisdp] succeeded!
```

### Install mqtt client

Requires MQTT 5.0 client, such as Mosquitto clients.

```bash
$ brew install mosquitto
```

### Verify behavior

Connect two subsribing clients first, then send `Hello, world` from publishing client.

```bash
$ mosquitto_sub -V '5' -t 'zilla' -d
Client null sending CONNECT
Client 43516069-9fa3-493d-9ab1-17e5e891e5be received CONNACK (0)
Client 43516069-9fa3-493d-9ab1-17e5e891e5be sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 43516069-9fa3-493d-9ab1-17e5e891e5be received SUBACK
Subscribed (mid: 1): 0
Client 43516069-9fa3-493d-9ab1-17e5e891e5be received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```
```bash
$ mosquitto_sub -V '5' -t 'zilla' --cafile test-ca.crt -d
Client null sending CONNECT
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318  received CONNACK (0)
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318  sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318  received SUBACK
Subscribed (mid: 1): 0
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```
```bash
$ mosquitto_pub -V '5' -t 'zilla' -m 'Hello, world' -d
Client null sending CONNECT
Client 5beb7f61-1b92-460c-8a2d-30a38156c601 received CONNACK (0)
Client 5beb7f61-1b92-460c-8a2d-30a38156c601 sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client 5beb7f61-1b92-460c-8a2d-30a38156c601 sending DISCONNECT
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-mqtt-echo --namespace zilla-mqtt-echo
release "zilla-mqtt-echo" uninstalled
+ kubectl delete namespace zilla-mqtt-echo
namespace "zilla-mqtt-echo" deleted
```
