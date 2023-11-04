# http.kafka.sasl.scram

Listens on http port `7114` or https port `7143` and will produce messages to the `events` topic in `SASL/SCRAM` enabled Kafka, synchronously.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- kcat

### Install kcat client

Requires Kafka client, such as `kcat`.

```bash
brew install kcat
```

### Setup

The `setup.sh` script:

- installs Zilla, Kafka and Zookeeper to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `events` topic in Kafka
- creates SCRAM credential `user` (the default implementation of SASL/SCRAM in Kafka stores SCRAM credentials in ZooKeeper)
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ helm upgrade --install zilla-http-kafka-sasl-scram oci://ghcr.io/aklivity/charts/zilla --namespace zilla-http-kafka-sasl-scram --create-namespace --wait [...]
NAME: zilla-http-kafka-sasl-scram
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-sasl-scram
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm upgrade --install zilla-http-kafka-sasl-scram-kafka chart --namespace zilla-http-kafka-sasl-scram --create-namespace --wait
NAME: zilla-http-kafka-sasl-scram-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-sasl-scram
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-kafka-sasl-scram --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-1234567890-abcde
+ kubectl exec --namespace zilla-http-kafka-sasl-scram pod/kafka-1234567890-abcde --container kafka -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic events --if-not-exists
Created topic events.
+ kubectl exec --namespace zilla-http-kafka-sasl-scram pod/kafka-1234567890-abcde --container kafka -- /opt/bitnami/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 --alter --add-config 'SCRAM-SHA-256=[iterations=8192,password=bitnami],SCRAM-SHA-512=[password=bitnami]' --entity-type users --entity-name user
Completed updating config for user user.
+ nc -z localhost 7114
+ kubectl port-forward --namespace zilla-http-kafka-sasl-scram service/zilla 7114 7143
+ kubectl port-forward --namespace zilla-http-kafka-sasl-scram service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 7114
Connection to localhost port 7114 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

Send a `POST` request with an event body.

```bash
curl -v \
       -X "POST" http://localhost:7114/events \
       -H "Content-Type: application/json" \
       -d "{\"greeting\":\"Hello, world\"}"
```

output:

```text
...
> POST /events HTTP/1.1
> Content-Type: application/json
...
< HTTP/1.1 204 No Content
```

Verify that the event has been produced to the `events` Kafka topic.

```bash
kcat -C -b localhost:9092 -t events -J -u | jq .
```

output:

```json
{
  "topic": "events",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1652465273281,
  "broker": 1001,
  "headers": [
    "content-type",
    "application/json"
  ],
  "payload": "{\"greeting\":\"Hello, world\"}"
}
% Reached end of topic events [0] at offset 1
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla, Kafka and Zookeeper and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99998
99999
+ helm uninstall zilla-http-kafka-sasl-scram zilla-http-kafka-sasl-scram-kafka --namespace zilla-http-kafka-sasl-scram
release "zilla-http-kafka-sasl-scram" uninstalled
release "zilla-http-kafka-sasl-scram-kafka" uninstalled
+ kubectl delete namespace zilla-http-kafka-sasl-scram
namespace "zilla-http-kafka-sasl-scram" deleted
```
