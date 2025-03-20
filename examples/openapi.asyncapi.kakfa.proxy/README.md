# openapi.asyncapi.kakfa.proxy

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Test

#### Create Pet

```bash
curl 'http://localhost:7114/pets' \
     --header 'Content-Type: application/json' \
     --header 'Idempotency-Key: 1' \
     --data '{ "id": 1, "name": "Spike" }'
```

#### Retrieve Pets

```bash
curl 'http://localhost:7114/pets' --header 'Accept: application/json'
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```

