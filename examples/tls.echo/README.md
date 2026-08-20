# tls.echo

Listens on tls port `23456` and will echo back whatever is sent to the server.

## Requirements

- docker compose
- openssl

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
```

output:

```text
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
```

Type a `Hello, world` message and press `enter`.

output:

```text
Hello, world
Hello, world
```

### Verify behavior with a guarded route

The `secure-echo` application protocol is routed only when the `x509` guard authorizes
the session from the client certificate presented during the handshake. Mutual TLS is
`requested`, not `required`, so the `echo` protocol above keeps working with no client
certificate at all.

Without a client certificate the handshake still completes, but the guarded route does
not authorize and no application stream is opened:

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn secure-echo
```

Both `Test Client CA` and `Test Other CA` are trusted by the server, so a certificate
signed by either one completes the handshake. Only `Test Client CA` is named by the
guard's `client` role, so a certificate from the other issuer is authenticated at the
transport layer yet still refused by the route:

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -cert other.crt -key other.key -quiet -alpn secure-echo
```

A certificate from the named issuer authorizes the route, and the echo succeeds:

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -cert client.crt -key client.key -quiet -alpn secure-echo
```

Type a `Hello, world` message and press `enter`.

output:

```text
Hello, world
Hello, world
```

The only difference between the last two commands is which certificate is presented, so
the guard reading that certificate is what decides whether the route authorizes.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
