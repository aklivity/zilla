# asyncapi.http.kafka.proxy

In this guide, you create Kafka topics and use Zilla to implement the common Petstore example where requests are proxied to Kafka. Zilla is implementing the REST endpoints defined in an AsyncAPI 3.x spec and proxying them onto Kafka topics defined in an AsyncAPI 3.x spec based on the operations defined in each spec.

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Using this example

#### Synchronous APIs

The `/pet` endpoint proxies to Kafka synchronously meaning they will behave like a normal rest endpoint where the message persists on a kafka topics.

Create a pet using the `/pets` endpoint in the implemented API.

```bash
curl -v --location 'http://localhost:7114/pets' \
--header 'Content-Type: application/json' \
--data '{
  "name": "Rocky",
  "id": 1
}'
```

output:

```
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> POST /pets HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.88.1
> Accept: */*
> Content-Type: application/json
> Content-Length: 32
>
< HTTP/1.1 204 No Content
< Access-Control-Allow-Origin: *
<
```

List all the pets using `GET` request for the `/pets` endpoint

```bash
curl -v --location 'http://localhost:7114/pets' \
--header 'Accept: application/json'
```

output:

```
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> GET /pets HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.88.1
> Accept: application/json
>
< HTTP/1.1 200 OK
< Content-Length: 32
< Content-Type: application/json
< Etag: AQIAAg==
< Access-Control-Allow-Origin: *
< Access-Control-Expose-Headers: *
<
{
  "name": "Rocky",
  "id": 1
}
```

#### Asynchronous APIs

The `/customer` endpoint is an asynchronous endpoint meaning it will success with a `202 ACCEPTED` response and include a `Location` header that will include the correlation id used in the `/customer;cid={correlationId}` endpoint.

- The [petstore-customers](http://localhost:8080/ui/clusters/localhost/all-topics/petstore-pets/messages) Kafka topic will have all the pending customer object you posted with a `zilla:correlation-id` header on the kafka message.
- The [petstore-verified-customers](http://localhost:8080/ui/clusters/localhost/all-topics/petstore-pets/messages) Kafka topic will have all the verified customers and will need to include a matching `zilla:correlation-id` header to align with the message on the initial topic.

Create a non-verified customer using a `POST` request for the `/customer` endpoint

```bash
curl -v --location --globoff 'http://localhost:7114/customer' \
--header 'Prefer: respond-async' \
--header 'Content-Type: application/json' \
--data '{
    "id": 200000,
    "username": "fehguy",
    "status": "pending",
    "address": [
        {
            "street": "437 Lytton",
            "city": "Palo Alto",
            "state": "CA",
            "zip": "94301"
        }
    ]
}'
```

output:

```
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> POST /customer HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.88.1
> Accept: */*
> Prefer: respond-async
> Content-Type: application/json
> Content-Length: 238
>
< HTTP/1.1 202 Accepted
< Content-Length: 0
< Location: /customer;cid={correlationId}
< Access-Control-Allow-Origin: *
< Access-Control-Expose-Headers: *
<
```

Copy the location, and create a `GET` async request using the location as the path.
Note that the response will not return until you complete the following step to produce the response with `kafkacat`.

```bash
curl -v --location 'http://localhost:7114/customer;cid={correlationId}' \
--header 'Prefer: wait=1902418' \
--header 'Accept: application/json'
```

output:

```
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> GET /customer;cid=d9037ed5-a073-4a21-932d-2392a707800f-badc2e5c2abc25956e9d23d897d5db85 HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.88.1
> Prefer: wait=1902418
> Accept: application/json
>
< HTTP/1.1 200 OK
< Content-Length: 135
< Access-Control-Allow-Origin: *
<
{"id":200000,"username":"fehguy","status":"approved","address":[{"street":"437 Lytton","city":"Palo Alto","state":"CA","zip":"94301"}]}%
```

Using `kafkacat` and the copied `correlation-id` produce the correlated message:

```sh
echo '{"id":200000,"username":"fehguy","status":"approved","address":[{"street":"437 Lytton","city":"Palo Alto","state":"CA","zip":"94301"}]}' | docker compose -p zilla-asyncapi-http-kafka-proxy exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -k "c234d09b-2fdf-4538-9d31-27c8e2912d4e" \
    -t petstore-verified-customers \
    -H "zilla:correlation-id={correlationId}"
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```

