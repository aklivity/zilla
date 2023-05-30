# sse.kafka.fanout

Listens on http port `8080` or https port `9090` and will stream back whatever is published to the `events` topic in Kafka.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- sse-cat
- kcat

### Install sse-cat client

Requires Server-Sent Events client, such as `sse-cat` version `2.0.5` or higher on `node` version `14` or higher.

```bash
npm install -g sse-cat
```

### Install kcat client

Requires Kafka client, such as `kcat`.

```bash
brew install kcat
```

### Setup

The `setup.sh` script:

- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- copies the contents of the www directory to the Zilla pod
- creates the `events` topic in Kafka with the `cleanup.policy=compact` topic configuration.
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-sse-kafka-fanout oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-sse-kafka-fanout --create-namespace --wait [...]
NAME: zilla-sse-kafka-fanout
LAST DEPLOYED: [...]
NAMESPACE: zilla-sse-kafka-fanout
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-sse-kafka-fanout-kafka chart --namespace zilla-sse-kafka-fanout --create-namespace --wait
NAME: zilla-sse-kafka-fanout-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-sse-kafka-fanout
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-sse-kafka-fanout --selector app.kubernetes.io/instance=zilla -o json
++ jq -r '.items[0].metadata.name'
+ ZILLA_POD=zilla-1234567890-abcde
+ kubectl cp --namespace zilla-sse-kafka-fanout www zilla-1234567890-abcde:/var/
++ kubectl get pods --namespace zilla-sse-kafka-fanout --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-1234567890-abcde
+ kubectl exec --namespace zilla-sse-kafka-fanout pod/kafka-1234567890-abcde -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic events --config cleanup.policy=compact --if-not-exists
Created topic events.
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-sse-kafka-fanout service/zilla-sse-kafka-fanout 8080 9090
+ kubectl port-forward --namespace zilla-sse-kafka-fanout service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

Connect `sse-cat` client first, then send `Hello, world ...` from `kcat` producer client.
Note that the `Hello, world ...` message will not arrive until after using `kcat` to produce the `Hello, world ...` message in the next step.

```bash
sse-cat http://localhost:8080/events
```

output:

```text
Hello, world ...
```

```bash
echo "Hello, world `date`" | kcat -P -b localhost:9092 -t events -k 1
```

Note that only the latest messages with distinct keys are guaranteed to be retained by a compacted Kafka topic, so use different values for `-k` above to retain more than one message in the `events` topic.

### Browser

Browse to `https://localhost:9090/index.html` and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click the `Go` button to attach the browser SSE event source to Kafka via Zilla.

All non-compacted messages with distinct keys in the `events` Kafka topic are replayed to the browser.

Open the browser developer tools console to see additional logging, such as the `open` event.

Additional messages produced to the `events` Kafka topic then arrive at the browser live.

### Reliability

Simulate connection loss by stopping the `zilla` service in the `docker` stack.

```
kubectl scale --replicas=0 --namespace=zilla-sse-kafka-fanout deployment/zilla-sse-kafka-fanout
```

This causes errors to be logged in the browser console during repeated attempts to automatically reconnect.

Simulate connection recovery by starting the `zilla` service again.

```
kubectl scale --replicas=1 --namespace=zilla-sse-kafka-fanout deployment/zilla-sse-kafka-fanout
```

Now you need to restart the port-forward.

```bash
kubectl port-forward --namespace zilla-sse-kafka-fanout service/zilla-sse-kafka-fanout 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
```

Any messages produced to the `events` Kafka topic while the browser was attempting to reconnect are now delivered immediately.

Additional messages produced to the `events` Kafka topic then arrive at the browser live.

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99999
99998
+ killall kubectl
+ helm uninstall zilla-sse-kafka-fanout zilla-sse-kafka-fanout-kafka --namespace zilla-sse-kafka-fanout
release "zilla-sse-kafka-fanout" uninstalled
release "zilla-sse-kafka-fanout-kafka" uninstalled
+ kubectl delete namespace zilla-sse-kafka-fanout
namespace "zilla-sse-kafka-fanout" deleted
```
