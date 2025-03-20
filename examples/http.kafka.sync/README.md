# http.kafka.sync

Listens on http port `7114` to correlate requests and responses over the `items-requests`
and `items-responses` topics in Kafka, synchronously.

## Setup

Start this example by running this command from the example directory.

```bash
docker compose up -d
```

This will:

- Install Zilla and Kafka
- Create the `items-requests` and `items-responses` topics in Kafka
- To restart Zilla to update the deployment run

  ```bash
  docker compose up -d --force-recreate --no-deps zilla
  ```

## Verify behavior

Send a `PUT` request for a specific item.
Note that the response will not return until you complete the following step to produce the response with `kafkacat`.

```bash
curl -v \
  -X "PUT" http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
  -H "Idempotency-Key: 1" \
  -H "Content-Type: application/json" \
  -d "{\"greeting\":\"Hello, world\"}"
```

```text
...
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> PUT /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.86.0
> Accept: */*
> Idempotency-Key: 1
> Content-Type: application/json
> Content-Length: 27
>
```

Fetch the request message, then send the correlated response via the Kafka UI [items-requests](http://localhost:8080/ui/clusters/local/all-topics/items-requests) topic.

- or from the command line

  ```bash
  docker compose -p zilla-http-kafka-sync exec kafkacat \
    kafkacat -b kafka.examples.dev:29092 -C -f 'Key:Message | %k:%s\n Headers | %h \n\n' -t items-requests
  ```

  ```text
  Key:Message | 5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07:{"greeting":"Hello, world"}
   Headers | :scheme=http,:method=PUT,:path=/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07,:authority=localhost:7114,user-agent=curl/7.86.0,accept=*/*,idempotency-key=1,content-type=application/json,zilla:reply-to=items-responses,zilla:correlation-id=1-b664e68dd5c49716688e3ec1c8c68bb6
  ```

Produce the the correlated response message via the Kafka UI [items-responses](http://localhost:8080/ui/clusters/local/all-topics/items-responses) topic.

Make sure to propagate the request message `zilla:correlation-id` header, found in the request message, verbatim as a response message `zilla:correlation-id` header.

```bash
echo "{\"greeting\":\"Hello, world `date`\"}" | docker compose -p zilla-http-kafka-sync exec -T kafkacat \
  kafkacat -P \
  -b kafka.examples.dev:29092 \
  -t items-responses \
  -k "6cf7a1d5-3772-49ef-86e7-ba6f2c7d7d0" \
  -H ":status=200" \
  -H "zilla:correlation-id=1-f8f1c788ba786f691823098ee0505a1b"
```

The previous `PUT` request will complete.

```text
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Content-Length: 56
<
* Connection #0 to host localhost left intact
{"greeting":"Hello, world Thu Sep 19 15:30:48 EDT 2024"}%
```

Verify the response via the Kafka UI [items-responses](http://localhost:8080/ui/clusters/local/all-topics/items-responses) topic.

- or from the command line

  ```bash
  docker compose -p zilla-http-kafka-sync exec kafkacat \
    kafkacat -b kafka.examples.dev:29092 -C -f 'Key:Message | %k:%s\n Headers | %h \n\n' -t items-responses
  ```

  ```text
  Key:Message | 6cf7a1d5-3772-49ef-86e7-ba6f2c7d7d0:{"greeting":"Hello, world Thu Sep 19 15:30:48 EDT 2024"}
   Headers | :status=200,zilla:correlation-id=1-f8f1c788ba786f691823098ee0505a1b
  ```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
