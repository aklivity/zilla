# http.proxy.jwt

Listens on http port `7114` and will echo back whatever is sent to the server from an authorized client.
Listens on https port `7143` and will echo back whatever is sent to the server from an authorized client.

## Requirements

- docker compose
- jwt-cli

### Install jwt-cli client

Requires JWT command line client, such as `jwt-cli` version `2.0.0` or higher.

```bash
brew install mike-engel/jwt-cli/jwt-cli
```

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Create a token that is valid until 2032, but without `echo:stream` scope.

```bash
export JWT_TOKEN=$(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --secret @private.pem)
```

See the signed JWT token, without `echo:stream` scope, print the `JWT_TOKEN` var.

```bash
echo $JWT_TOKEN
```

Use the signed JWT token, without `echo:stream` scope, to attempt an authorized request.

```bash
curl -v http://localhost:7114/ \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: text/plain" \
    -d "Hello, world"
```

The request is rejected as expected, and without leaking any information about failed security checks.

```
< HTTP/1.1 404 Not Found
< Connection: close
<
```

Create a token with the `echo:stream` scope.

```bash
export JWT_TOKEN=$(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=echo:stream" \
    --secret @private.pem)
```

See the signed JWT token with `echo:stream` scope print the `JWT_TOKEN` var.

```bash
echo $JWT_TOKEN
```

Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request.

```bash
curl "http://localhost:7114/" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: text/plain" \
    -d "Hello, world"
```

The request is authorized and processed as expected.

```text
Hello, world
```

Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request via HTTP/2.

```bash
curl --cacert test-ca.crt "https://localhost:7143/" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: text/plain" \
    -d "Hello, world" \
    --http2-prior-knowledge
```

```text
Hello, world
```

Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request via HTTP/1.1 over TLS.

```bash
curl --cacert test-ca.crt "https://localhost:7143/" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: text/plain" \
    -d "Hello, world" \
    --http1.1
```

```text
Hello, world
```

Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request via HTTP/2 over TLS.

```bash
curl --cacert test-ca.crt "https://localhost:7143/" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: text/plain" \
    -d "Hello, world" \
    --http2
```

```text
Hello, world
```

### Note

The `private.pem` key was generated using `openssl` as follows.

```bash
openssl genrsa -out private.pem 2048
```

Then the RSA key modulus is extracted in base64 format.

```bash
openssl rsa -in private.pem -pubout -noout -modulus | cut -d= -f2 | xxd -r -p | base64
```

The resulting base64 modulus is used to configure the `jwt` guard in `zilla.yaml` to validate the integrity of signed JWT tokens.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
