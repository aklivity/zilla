# asyncapi.sse.kafka.proxy

In this guide, you create Kafka topics and use Zilla to implement an SSE API where Zilla listens on http port 7114 and will stream back whatever is published to the events topic in Kafka.
Zilla is implementing the SSE endpoints defined in an AsyncAPI 3.x spec and proxying them onto Kafka topics defined in an AsyncAPI 3.x spec based on the operations defined in each spec.

## Running locally

This example can be run using Docker compose or Kubernetes. The setup scripts are in the [compose](./docker/compose) and [helm](./k8s/helm) folders respectively and work the same way.

You will need a running kafka broker. To start one locally you will find instructions in the [kafka.broker](../kafka.broker) folder. Alternatively you can use the [redpanda.broker](../redpanda.broker) folder.

### Setup

Whether you chose [compose](./docker/compose) or [helm](./k8s/helm), the `setup.sh` script will:

- create the necessary kafka topics
- create the Eventstore API at `http://localhost:7114`

```bash
./setup.sh
```

### Verify behaviour

Using `curl` client connect to the SSE stream.
```bash
curl -N --http2 -H "Accept:text/event-stream" -v "http://localhost:7114/events"
```

output:

```
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> GET /events HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.88.1
> Connection: Upgrade, HTTP2-Settings
> Upgrade: h2c
> HTTP2-Settings: AAMAAABkAAQCAAAAAAIAAAAA
> Accept:text/event-stream
>
< HTTP/1.1 200 OK
< Content-Type: text/event-stream
< Transfer-Encoding: chunked
< Access-Control-Allow-Origin: *
<
id:AQIAAg==
data:{ "id": 1, "name": "Hello World!"}
```

In another terminal window use `kcat` to publish to the `events` Kafka topic
```bash
echo '{ "id": 1, "name": "Hello World!"}' | \
    kcat -P \
         -b localhost:29092 \
         -k "1" -t events
```

On the `curl` client, the event should appear.

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
