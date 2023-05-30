# http.kafka.oneway

Listens on http port `8080` or https port `9090` and will produce messages to the `events` topic in Kafka, synchronously.

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

- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `events` topic in Kafka.
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-http-kafka-oneway oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-http-kafka-oneway --create-namespace --wait [...]
NAME: zilla-http-kafka-oneway
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-oneway
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-http-kafka-oneway-kafka chart --namespace zilla-http-kafka-oneway --create-namespace --wait
NAME: zilla-http-kafka-oneway-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-oneway
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-kafka-oneway --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-1234567890-abcde
+ kubectl exec --namespace zilla-http-kafka-oneway pod/kafka-1234567890-abcde -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic events --if-not-exists
Created topic events.
+ kubectl port-forward --namespace zilla-http-kafka-oneway service/zilla-http-kafka-oneway-kafka 8080 9090
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-http-kafka-oneway service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

Send a `POST` request with an event body.

```bash
curl -v \
       -X "POST" http://localhost:8080/events \
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
```

output:

```text
% Reached end of topic events [0] at offset 1
```

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
+ helm uninstall zilla-http-kafka-oneway zilla-http-kafka-oneway-kafka --namespace zilla-http-kafka-oneway
release "zilla-http-kafka-oneway" uninstalled
release "zilla-http-kafka-oneway-kafka" uninstalled
+ kubectl delete namespace zilla-http-kafka-oneway
namespace "zilla-http-kafka-oneway" deleted
```
