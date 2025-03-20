# mqtt.proxy.jwt

Listens on mqtt port `7183` and `7883` forwarding mqtt publish messages and proxies subscribes to mosquitto MQTT broker listening on `1883`.

## Requirements

- jq, nc
- docker compose
- mosquitto

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Install jwt-cli client

Requires JWT command line client, such as `jwt-cli` version `2.0.0` or higher.

```bash
brew install mike-engel/jwt-cli/jwt-cli
```

### Verify behavior

Create a token without `mqtt:stream` scope.

```bash
export MQTT_USERNAME="Bearer $(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --secret @private.pem)"
```

Create a token that is valid until 2032, with `mqtt:stream` scope.

See the signed JWT token, without `mqtt:stream` scope, print the `MQTT_USERNAME` var.

```bash
$ jwt encode \
echo $MQTT_USERNAME
```

Use the signed JWT token, without `mqtt:stream` scope, to attempt an authorized request. Provide the JWT token in the MQTT_USERNAME field.

```bash
docker compose -p zilla-mqtt-proxy-jwt exec mosquitto-cli \
  mosquitto_sub --url mqtt://zilla.examples.dev:7183/zilla --debug -u $MQTT_USERNAME
```

The request is rejected as expected, and without leaking any information about failed security checks.

```text
Client null sending CONNECT
Client null sending CONNECT
Client null sending CONNECT
```

Create a token with the `mqtt:stream` scope.

```bash
export MQTT_USERNAME="Bearer $(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=mqtt:stream" \
    --secret @private.pem)"
```

See the signed JWT token with `mqtt:stream` scope print the `MQTT_USERNAME` var.

```bash
echo $MQTT_USERNAME
```

Use the signed JWT token, with `mqtt:stream` scope, to attempt an authorized request.

```bash
docker compose -p zilla-mqtt-proxy-jwt exec mosquitto-cli \
  mosquitto_sub --url mqtt://zilla.examples.dev:7183/zilla --debug -u $MQTT_USERNAME
```

The connection is authorized.

```text
Client null sending CONNECT
Client a0b72aaa-3d12-4d1d-8fc3-4971d1973763 received CONNACK (0)
Client a0b72aaa-3d12-4d1d-8fc3-4971d1973763 sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client a0b72aaa-3d12-4d1d-8fc3-4971d1973763 received SUBACK
Subscribed (mid: 1): 0
Client 2b77314a-163f-4f18-908c-2913645e4f56 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world
```

Use the signed JWT token, with `mqtt:stream` scope, publish a message.

```bash
docker compose -p zilla-mqtt-proxy-jwt exec mosquitto-cli \
  mosquitto_pub --url mqtt://zilla.examples.dev:7183/zilla --message 'Hello, world' --debug -u $MQTT_USERNAME
```

output:

```text
Client null sending CONNECT
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 received CONNACK (0)
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client 44181407-f1bc-4a6b-b94d-9f37d37ea395 sending DISCONNECT
```

### Note

The `private.pem` key was generated using `openssl` as follows.

```bash
openssl genrsa -out private.pem 2048
```

Then the RSA key modulus is extracted in base64 format.

```bash
openssl rsa -in private.pem -pubout -noout -modulus | cut -h 'localhost' -p 7183 --debug= -f2 | xxd -r -p | base64
```

The resulting base64 modulus is used to configure the `jwt` guard in `zilla.yaml` to validate the integrity of signed JWT tokens.

### Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```

