# mqtt.kafka.broker.jwt

Listens on mqtt port `7183` and will forward mqtt publish messages from an authorized mqtt client to Kafka, delivering to all authorized mqtt clients subscribed to the same topic.
Listens on mqtts port `7883` and will forward mqtt publish messages from an authorized mqtt client to Kafka, delivering to all authorized mqtt clients subscribed to the same topic.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- mosquitto
- kcat

### Install jwt-cli client

Requires JWT command line client, such as `jwt-cli` version `2.0.0` or higher.

```bash
brew install mike-engel/jwt-cli/jwt-cli
```

### Install mqtt client

Requires MQTT 5.0 client, such as Mosquitto clients.

```bash
brew install mosquitto
```

### Setup

The `setup.sh` script:

- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- starts port forwarding

```bash
./setup.sh
```

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ helm upgrade --install zilla-mqtt-kafka-broker-jwt oci://ghcr.io/aklivity/charts/zilla --namespace zilla-mqtt-kafka-broker-jwt --create-namespace --wait [...]
NAME: zilla-mqtt-kafka-broker-jwt
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-kafka-broker-jwt
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
+ helm upgrade --install zilla-mqtt-kafka-broker-jwt-kafka chart --namespace zilla-mqtt-kafka-broker-jwt --create-namespace --wait
NAME: zilla-mqtt-kafka-broker-jwt-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-kafka-broker-jwt
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-mqtt-kafka --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-74675fbb8-g56l9
+ kubectl exec --namespace zilla-mqtt-kafka pod/kafka-74675fbb8-g56l9 -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic mqtt-messages --if-not-exists
Created topic mqtt-messages.
+ kubectl port-forward --namespace zilla-mqtt-kafka service/zilla 7183 7883
+ nc -z localhost 7183
+ kubectl port-forward --namespace zilla-mqtt-kafka service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 7183
Connection to localhost port 7183 [tcp/ibm-mqisdp] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

Create a token without `mqtt:stream` scope.

```bash
export MQTT_USERNAME="Bearer $(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --secret @private.pem)"
```
Create a token that is valid until 2032, with `mqtt:stream` scope.

See the signed JWT token, without `mqtt:stream` scope, print the `MQTT_USERNAME` var.

```bash
$ jwt encode \
echo $MQTT_USERNAME
```

Use the signed JWT token, without `mqtt:stream` scope, to attempt an authorized request. Provide the JWT token in the MQTT_USERNAME field.

```bash
mosquitto_sub -V 'mqttv5' --topic 'zilla' -h 'localhost' -p 7883 --debug -u $MQTT_USERNAME
```

The request is rejected as expected, and without leaking any information about failed security checks.

```text
Client null sending CONNECT
Client null sending CONNECT
Client null sending CONNECT
```

Create a token with the `mqtt:stream` scope.

```bash
export MQTT_USERNAME="Bearer $(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=mqtt:stream" \
    --secret @private.pem)"
```

See the signed JWT token with `mqtt:stream` scope print the `MQTT_USERNAME` var.

```bash
echo $MQTT_USERNAME
```

Use the signed JWT token, with `mqtt:stream` scope, to attempt an authorized request.

```bash
mosquitto_sub -V 'mqttv5' --topic 'zilla' -h 'localhost' -p 7183 --debug -u $MQTT_USERNAME
```

The connection is authorized.

```text
Client null sending CONNECT
Client a0b72aaa-3d12-4d1d-8fc3-4971d1973763 received CONNACK (0)
Client a0b72aaa-3d12-4d1d-8fc3-4971d1973763 sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client a0b72aaa-3d12-4d1d-8fc3-4971d1973763 received SUBACK
Subscribed (mid: 1): 0
Client 2b77314a-163f-4f18-908c-2913645e4f56 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```

Use the signed JWT token, with `mqtt:stream` scope, publish a message.

```bash
mosquitto_pub -V 'mqttv5' --topic 'zilla' -m 'Hello, world' -h 'localhost' -p 7183 --debug -u $MQTT_USERNAME
```

output:

```text
Client null sending CONNECT
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 received CONNACK (0)
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 sending DISCONNECT
```


### Note

The `private.pem` key was generated using `openssl` as follows.

```bash
openssl genrsa -out private.pem 2048
```

Then the RSA key modulus is extracted in base64 format.

```bash
openssl rsa -in private.pem -pubout -noout -modulus | cut -h 'localhost' -p 7183 --debug= -f2 | xxd -r -p | base64
```

The resulting base64 modulus is used to configure the `jwt` guard in `zilla.yaml` to validate the integrity of signed JWT tokens.

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
16976
16977
17117
+ killall kubectl
+ helm uninstall zilla-mqtt-kafka-broker-jwt zilla-mqtt-kafka-broker-jwt-kafka --namespace zilla-mqtt-kafka-broker-jwt
release "zilla-mqtt-kafka-broker-jwt" uninstalled
release "zilla-mqtt-kafka-broker-jwt-kafka" uninstalled
+ kubectl delete namespace zilla-mqtt-kafka-broker-jwt
namespace "zilla-mqtt-kafka-broker-jwt" deleted
```
