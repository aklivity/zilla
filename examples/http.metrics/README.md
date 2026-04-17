# http.metrics

Demonstrates HTTP metrics with arbitrary per-request attributes (method, path) exported via Prometheus.

Listens on `http://localhost:7114/` and echoes back requests. Metrics are broken down by HTTP method and path, visible at `http://localhost:7190/metrics`.

## Requirements

- curl
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Send some requests:

```bash
curl -d "Hello, world" http://localhost:7114/items
curl http://localhost:7114/users
curl -X POST -d '{"name":"test"}' http://localhost:7114/items
```

Check Prometheus metrics with per-attribute breakdowns:

```bash
curl http://localhost:7190/metrics
```

output:

```text
# HELP http_request_size HTTP request content length
# TYPE http_request_size histogram
http_request_size_bucket{le="2",namespace="example",binding="north_http_server",method="POST",path="/items"} 0
...

# HELP http_duration HTTP request-response duration
# TYPE http_duration histogram
http_duration_bucket{le="2",namespace="example",binding="north_http_server",method="GET",path="/users"} 1
...
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
