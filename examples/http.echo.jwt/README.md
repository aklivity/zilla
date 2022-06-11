# http.echo.jwt
Listens on http port `8080` and will echo back whatever is sent to the server from an authorized client.
Listens on https port `9090` and will echo back whatever is sent to the server from an authorized client.

### Requirements
 - Docker 20.10+
 - jwt-cli

### Install jwt-cli client
Requires JWT command line client, such as `jwt-cli` version `2.0.0` or higher.
```bash
brew install mike-engel/jwt-cli/jwt-cli
```

### Start zilla engine
```bash
docker stack deploy -c stack.yml example
```
```
Creating network example_net0
Creating service example_zilla
```

### Verify behavior
Create a token that is valid until 2032, but without `echo:stream` scope.
```bash
jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp 1968862747 \
    --no-iat \
    --secret @private.pem
```
The signed JWT token is shown below.
```
eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJlY2hvOnN0cmVhbSJ9.glfCpnhVkQFf5zXlSFDWYsHyFhFEuxmRXVu8AbFXh67FzcjwzEcMgw1Zt7_SETyXHpNl1HhOgLohaVCkGxVG2iiOq0MJO00_l6X125itdY37noOFiGWTHb8uosGI4V3NhhCKyoVLtl3b9X4c6pCxHoQo7XkT1xmcjSeCKQenXpuX5WnKMIZsyBxUsOxg1cv3K7mg6WnKOlXWGjvCAoomUjIGiGDruFQMP1UzniMgY0b0IrofijiNB3HEKQQcU44MD7jH9lldrea1vaKnxYwmiaq7g7RsYMFXeNLzWz6hY61ColSeEUCiDtpVSNCyjKZHkuLA7yLQ-pvipwCpT0jU1Q
```
Use the signed JWT token, without `echo:stream` scope, to attempt an authorized request.
```bash
curl -v http://localhost:8080/ \
    -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIn0.k4Aq93RzFpOBBwuUEewJUq1Wj1F0csfW4c_eGaQY9xNk8WC1C_rhmWkiprApkoVoaUJI7PVemUFfwKmx3XVWTYB3AQUihGGDKA6TRN2kTfkd1Vm_tBbn6a1nsUKbfl70vFD51jebJ9w5yG2b_jEiqtt6eOW99KNNRdAi5U0z7NHHIniu8Yfi5qrK0IBJBBWOoe-D-539ZzWWlMZKA5n1BJZ6x5ZOJAbYWdoMxr73uo7p9rWdVNk-61KsqVSSkCy92dq_d0Uoa3Q8xT5cwpWoljDwl-jB1O6PBwR7MVGJfihFfQVimt0NDnWi8TPXyBUq7RWGwfmQdEHwcrGAnJiaNg" \
    -H "Content-Type: text/plain" \
    -d "Hello, world"
```
The request is rejected as expected, and without leaking any information about failed security checks.
```
< HTTP/1.1 404 Not Found
< Connection: close
< 
```
Create a token that is valid until 2032, with `echo:stream` scope.
```bash
jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp 1968862747 \
    --no-iat \
    --payload "scope=echo:stream" \
    --secret @private.pem
```
The signed JWT token with `echo:stream` scope is shown below.
```
eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJlY2hvOnN0cmVhbSJ9.glfCpnhVkQFf5zXlSFDWYsHyFhFEuxmRXVu8AbFXh67FzcjwzEcMgw1Zt7_SETyXHpNl1HhOgLohaVCkGxVG2iiOq0MJO00_l6X125itdY37noOFiGWTHb8uosGI4V3NhhCKyoVLtl3b9X4c6pCxHoQo7XkT1xmcjSeCKQenXpuX5WnKMIZsyBxUsOxg1cv3K7mg6WnKOlXWGjvCAoomUjIGiGDruFQMP1UzniMgY0b0IrofijiNB3HEKQQcU44MD7jH9lldrea1vaKnxYwmiaq7g7RsYMFXeNLzWz6hY61ColSeEUCiDtpVSNCyjKZHkuLA7yLQ-pvipwCpT0jU1Q
```
Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request.
```bash
curl "http://localhost:8080/" \
    -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJlY2hvOnN0cmVhbSJ9.glfCpnhVkQFf5zXlSFDWYsHyFhFEuxmRXVu8AbFXh67FzcjwzEcMgw1Zt7_SETyXHpNl1HhOgLohaVCkGxVG2iiOq0MJO00_l6X125itdY37noOFiGWTHb8uosGI4V3NhhCKyoVLtl3b9X4c6pCxHoQo7XkT1xmcjSeCKQenXpuX5WnKMIZsyBxUsOxg1cv3K7mg6WnKOlXWGjvCAoomUjIGiGDruFQMP1UzniMgY0b0IrofijiNB3HEKQQcU44MD7jH9lldrea1vaKnxYwmiaq7g7RsYMFXeNLzWz6hY61ColSeEUCiDtpVSNCyjKZHkuLA7yLQ-pvipwCpT0jU1Q" \
    -H "Content-Type: text/plain" \
    -d "Hello, world"
```
The request is authorized and processed as expected.
```
Hello, world
```
Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request via HTTP/2.
```bash
curl --cacert test-ca.crt "https://localhost:9090/" \
    -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJlY2hvOnN0cmVhbSJ9.glfCpnhVkQFf5zXlSFDWYsHyFhFEuxmRXVu8AbFXh67FzcjwzEcMgw1Zt7_SETyXHpNl1HhOgLohaVCkGxVG2iiOq0MJO00_l6X125itdY37noOFiGWTHb8uosGI4V3NhhCKyoVLtl3b9X4c6pCxHoQo7XkT1xmcjSeCKQenXpuX5WnKMIZsyBxUsOxg1cv3K7mg6WnKOlXWGjvCAoomUjIGiGDruFQMP1UzniMgY0b0IrofijiNB3HEKQQcU44MD7jH9lldrea1vaKnxYwmiaq7g7RsYMFXeNLzWz6hY61ColSeEUCiDtpVSNCyjKZHkuLA7yLQ-pvipwCpT0jU1Q" \
    -H "Content-Type: text/plain" \
    -d "Hello, world" \
    --http2-prior-knowledge
```
```
Hello, world
```
Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request via HTTP/1.1 over TLS.
```bash
curl --cacert test-ca.crt "https://localhost:9090/" \
    -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJlY2hvOnN0cmVhbSJ9.glfCpnhVkQFf5zXlSFDWYsHyFhFEuxmRXVu8AbFXh67FzcjwzEcMgw1Zt7_SETyXHpNl1HhOgLohaVCkGxVG2iiOq0MJO00_l6X125itdY37noOFiGWTHb8uosGI4V3NhhCKyoVLtl3b9X4c6pCxHoQo7XkT1xmcjSeCKQenXpuX5WnKMIZsyBxUsOxg1cv3K7mg6WnKOlXWGjvCAoomUjIGiGDruFQMP1UzniMgY0b0IrofijiNB3HEKQQcU44MD7jH9lldrea1vaKnxYwmiaq7g7RsYMFXeNLzWz6hY61ColSeEUCiDtpVSNCyjKZHkuLA7yLQ-pvipwCpT0jU1Q" \
    -H "Content-Type: text/plain" \
    -d "Hello, world" \
    --http1.1
```
```
Hello, world
```
Use the signed JWT token, with `echo:stream` scope, to attempt an authorized request via HTTP/2 over TLS.
```bash
curl --cacert test-ca.crt "https://localhost:9090/" \
    -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTk2ODg2Mjc0NywiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJlY2hvOnN0cmVhbSJ9.glfCpnhVkQFf5zXlSFDWYsHyFhFEuxmRXVu8AbFXh67FzcjwzEcMgw1Zt7_SETyXHpNl1HhOgLohaVCkGxVG2iiOq0MJO00_l6X125itdY37noOFiGWTHb8uosGI4V3NhhCKyoVLtl3b9X4c6pCxHoQo7XkT1xmcjSeCKQenXpuX5WnKMIZsyBxUsOxg1cv3K7mg6WnKOlXWGjvCAoomUjIGiGDruFQMP1UzniMgY0b0IrofijiNB3HEKQQcU44MD7jH9lldrea1vaKnxYwmiaq7g7RsYMFXeNLzWz6hY61ColSeEUCiDtpVSNCyjKZHkuLA7yLQ-pvipwCpT0jU1Q" \
    -H "Content-Type: text/plain" \
    -d "Hello, world" \
    --http2
```
```
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
The resulting base64 modulus is used to configure the `jwt` guard in `zilla.json` to validate the integrity of signed JWT tokens.
