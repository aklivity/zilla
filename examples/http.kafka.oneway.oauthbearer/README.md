# http.kafka.oneway.oauthbearer

Listens on http port `7114` and will forward requests to the `events` topic in Kafka,
using `SASL/OAUTHBEARER` to authenticate Zilla to Kafka with a JWT token.

The default token embedded in [compose.yaml](compose.yaml) is pre-signed and valid until 2096.
Generate a new token only if you need a different subject, audience, or expiry.

## Requirements

- docker compose

## Generate JWT Token

The [private.pem](private.pem) RSA key signs tokens that Kafka validates against the public key
served by the `jwks` service at `http://jwks.examples.dev/jwks.json`.

### Using the included jwt-cli service

```bash
KAFKA_SASL_TOKEN=$(docker compose run --rm jwt-cli encode \
  --alg RS256 \
  --kid example \
  --iss "https://auth.example.com" \
  --aud "https://api.example.com" \
  --sub "zilla-service-account" \
  -P scope=kafka \
  --exp "+3650d" \
  --secret @/private.pem)
```

## Setup

To `start` the Docker Compose stack, optionally passing a custom token:

```bash
KAFKA_SASL_TOKEN=$KAFKA_SASL_TOKEN docker compose up -d
```

Or with the default embedded token:

```bash
docker compose up -d
```

### Verify behavior

Send a `POST` request to the `/events` endpoint.

```bash
curl -v -X POST http://localhost:7114/events \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello, world"}'
```

output:

```text
> POST /events HTTP/1.1
> Content-Type: application/json
...
< HTTP/1.1 204 No Content
```

Verify the event arrived in Kafka by opening the Kafka UI at [http://localhost:8080](http://localhost:8080),
selecting the `local` cluster, and browsing the `events` topic.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
