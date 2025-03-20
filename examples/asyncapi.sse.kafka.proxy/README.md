# asyncapi.sse.kafka.proxy

In this guide, you create Kafka topics and use Zilla to implement an SSE API where Zilla listens on http port 7114 and will stream back whatever is published to the events topic in Kafka.
Zilla is implementing the SSE endpoints defined in an AsyncAPI 3.x spec and proxying them onto Kafka topics defined in an AsyncAPI 3.x spec based on the operations defined in each spec.

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behaviour

Using `curl` client connect to the SSE stream.

```bash
curl -N --http2 -H "Accept:text/event-stream" "http://localhost:7114/events"
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

In another terminal window use `kafkacat` to publish to the `events` Kafka topic.

```bash
echo '{ "id": 1, "name": "Hello World!"}' | docker compose -p zilla-asyncapi-sse-kafka-proxy exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -k "1" -t events
```

On the `curl` client, the event should appear.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```

