# ws.proxy

WebSocket proxy example with TLS on the north side that exercises the HTTP client upgrade code path.

This example tests the window update flow after WebSocket upgrade through HTTP client binding.

## Flow

```
Client -> TCP server -> TLS server -> HTTP server -> WS server -> WS client -> HTTP client -> TCP client -> Backend
```

## Running

```bash
docker compose up -d
```

## Testing

Connect using websocat (from host):

```bash
websocat -k wss://localhost:7143 --ws-c-uri "wss://localhost/"
```

Or from inside the docker network:

```bash
docker compose exec websocat websocat -k wss://zilla.examples.dev:7143 --ws-c-uri "wss://localhost/"
```

Then type messages - they should be echoed back through the proxy chain.

## Cleanup

```bash
docker compose down
```
