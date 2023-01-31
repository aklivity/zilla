# http.kafka.sync

Listens on http port `8080` or https port `9090` and will correlate requests and responses over the `items-requests`
and `items-responses` topics in Kafka, synchronously.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- kcat

### Install kcat client

Requires Kafka client, such as `kcat`.

```bash
$ brew install kcat
```

### Setup

The `setup.sh` script:
- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `items-requests` and `items-responses` topics in Kafka
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-http-kafka-sync chart --namespace zilla-http-kafka-sync --create-namespace --wait
NAME: zilla-http-kafka-sync
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-sync
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-kafka-sync --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-1234567890-abcde
+ kubectl exec --namespace zilla-http-kafka-sync pod/kafka-1234567890-abcde -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic items-requests --if-not-exists
Created topic items-requests.
+ kubectl exec --namespace zilla-http-kafka-sync pod/kafka-1234567890-abcde -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic items-responses --if-not-exists
Created topic items-responses.
+ kubectl port-forward --namespace zilla-http-kafka-sync service/zilla 8080 9090
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-http-kafka-sync service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

Send a `PUT` request for a specific item.
Note that the response will not return until you complete the following step to produce the response with `kcat`.

```bash
$ curl -v \
       -X "PUT" http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "Idempotency-Key: 1" \
       -H "Content-Type: application/json" \
       -d "{\"greeting\":\"Hello, world\"}"
...
> PUT /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Idempotency-Key: 1
> Content-Type: application/json
...
< HTTP/1.1 200 OK
...
{"greeting":"Hello, world ..."}
```
Verify the request, then send the correlated response via the kafka `items-responses` topic.
```bash
$ kcat -C -b localhost:9092 -t items-requests -J -u | jq .
{
  "topic": "items-requests",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1652465273281,
  "broker": 1001,
  "headers": [
    ":scheme",
    "http",
    ":method",
    "PUT",
    ":path",
    "/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07",
    ":authority",
    "localhost:8080",
    "user-agent",
    "curl/7.79.1",
    "accept",
    "*/*",
    "idempotency-key",
    "1",
    "content-type",
    "application/json",
    "zilla:reply-to",
    "items-responses",
    "zilla:correlation-id",
    "1-e75a4e507cc0dc66a28f5a9617392fe8"
  ],
  "key": "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07",
  "payload": "{\"greeting\":\"Hello, world\"}"
}
% Reached end of topic items-requests [0] at offset 1
```
Make sure to propagate the request message `zilla:correlation-id` header verbatim as a response message `zilla:correlation-id` header.
```bash
$ echo "{\"greeting\":\"Hello, world `date`\"}" | \
    kcat -P \
         -b localhost:9092 \
         -t items-responses \
         -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
         -H ":status=200" \
         -H "zilla:correlation-id=1-e75a4e507cc0dc66a28f5a9617392fe8"
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
99998
+ killall kubectl
+ helm uninstall zilla-http-kafka-sync --namespace zilla-http-kafka-sync
release "zilla-http-kafka-sync" uninstalled
+ kubectl delete namespace zilla-http-kafka-sync
namespace "zilla-http-kafka-sync" deleted
```
