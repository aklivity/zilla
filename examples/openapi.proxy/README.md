# openapi.proxy

This example demonstrates creating an HTTP request proxy where the available endpoints are defined in an OpenAPI schema [petstore-openapi.yaml](./petstore-openapi.yaml).

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

## Test

```bash
curl 'http://localhost:7114/pets' --header 'Accept: application/json'
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
