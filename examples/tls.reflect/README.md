# tls.reflect

Listens on tls port `23456` and will echo back whatever is sent to the server, broadcasting to all clients.

## Requirements

- docker compose
- openssl

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.

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

Type a `Hello, one` message and press `enter`.

output:

```text
Hello, one
Hello, one
Hello, two
```

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

Type a `Hello, two` message and press `enter`.

output:

```text
Hello, one
Hello, two
Hello, two
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
