# sse.kafka.proxy (incubator)
Listens on https port `9090` and will stream back whatever is published to the `zilla` topic in Kafka.

### Requirements
 - Docker 20.10+

### Start kafka broker and zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
Creating service example_kafka
Creating service example_zookeeper
```

### Install sse-cat client
Requires Server-Sent Events client, such as `sse-cat` version `2.0.5` or higher on `node` version `14` or higher.
```bash
$ npm install -g sse-cat
```

### Install kcat client
Requires Kafka client, such as `kcat`.
```bash
$ brew install kcat
```

### Verify behavior
Connect `sse-cat` client first, then send `Hello, world` from `kcat` producer client.
```bash
$ sse-cat http://localhost:8080/zilla
Hello, world
```
```bash
$ echo "Hello, world `date`" | kcat -P -b localhost:9092 -t zilla
```
