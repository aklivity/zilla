# sse.proxy.jwt
Listens on https port `9090` and will stream back whatever is published to `sse_server` on tcp port `7001`.

### Requirements
 - Docker 20.10+
 - jwt-cli (https://github.com/mike-engel/jwt-cli)
 - nc (netcat)

### Install jwt-cli client
Generates JWT tokens from the command line.
```bash
$ brew install mike-engel/jwt-cli/jwt-cli
```

### Build the sse-server docker image
```
$ docker build -t zilla-examples/sse-server:latest .
...
 => exporting to image                                                                                                                                                                       1.4s 
 => => exporting layers                                                                                                                                                                      1.4s 
 => => writing image sha256:104abb7e5a4389c50d02aaf5d9bb2fef883c82e066ac2b400c9039b35086efcc                                                                                                 0.0s 
 => => naming to docker.io/zilla-examples/sse-server:latest
```

### Start SSE server and Zilla engine
```bash
$ docker stack deploy -c stack.yml example --resolve-image never
Creating network example_net0
Creating service example_sse
Creating service example_zilla
```

### Generate JWT token
Generate JWT token valid for `30 seconds` and signed by local private key.
```bash
$ export JWT_TOKEN=$(jwt encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --sub "example" \
    --exp 30s \
    --no-iat \
    --payload "scope=proxy:stream" \
    --secret @private.pem)
```

### Verify behavior
Connect `curl` client first, then send `Hello, world ...` from `nc` client.
Note that the `Hello, world ...` event will not arrive until after using `nc` to send the `Hello, world ...` message in the next step.
```bash
curl -v --cacert test-ca.crt "https://localhost:9090/events?access_token=${JWT_TOKEN}"
*   Trying 127.0.0.1:9090...
* Connected to localhost (127.0.0.1) port 9090 (#0)
* ALPN, offering h2
* ALPN, offering http/1.1
* successfully set certificate verify locations:
*  CAfile: test-ca.crt
*  CApath: none
* (304) (OUT), TLS handshake, Client hello (1):
* (304) (IN), TLS handshake, Server hello (2):
* (304) (IN), TLS handshake, Unknown (8):
* (304) (IN), TLS handshake, Certificate (11):
* (304) (IN), TLS handshake, CERT verify (15):
* (304) (IN), TLS handshake, Finished (20):
* (304) (OUT), TLS handshake, Finished (20):
* SSL connection using TLSv1.3 / AEAD-AES256-GCM-SHA384
* ALPN, server accepted to use h2
* Server certificate:
*  subject: C=US; ST=California; L=Palo Alto; O=Aklivity; OU=Development; CN=localhost
*  start date: Dec 21 23:04:14 2021 GMT
*  expire date: Dec 19 23:04:14 2031 GMT
*  common name: localhost (matched)
*  issuer: C=US; ST=California; L=Palo Alto; O=Aklivity; OU=Development; CN=Test CA
*  SSL certificate verify ok.
* Using HTTP2, server supports multiplexing
* Connection state changed (HTTP/2 confirmed)
* Copying HTTP/2 data in stream buffer to connection buffer after upgrade: len=0
* Using Stream ID: 1 (easy handle 0x7fe6bc011e00)
> GET /events HTTP/2
> Host: localhost:9090
> user-agent: curl/7.79.1
> accept: */*
> authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ.eyJhdWQiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbSIsImV4cCI6MTY2MTc5MDM1MCwiaXNzIjoiaHR0cHM6Ly9hdXRoLmV4YW1wbGUuY29tIiwic2NvcGUiOiJwcm94eTpzdHJlYW0iLCJzdWIiOiJleGFtcGxlIn0.XAugWfUFa-oU4Hx7Nn00zq9K9oTEkSknQvmiiAtJCRouIRXyl4qCAlQmOeI35JhN_RLj4p9EgoyCVtlZNWXKVcTeAxaAQrNeKywQ58wsn0VFdKHB2LXR0oxHXOtJIkl9oJWaM4IvUenKAfs2g-yHQtKNryhu9q8TgOPEW7JeqfCaV3J_xjn7WjMILggLde6lu8haGNa1ePDMxJwZ2Z9AQd-5Gcfyx9lQj_G7VQBHR5j8c5LrXx4U8E5f4KOYFUI7xs2wSuTApZyQdmetIRpFkIfsqVcH_rtdqs6ZuCTwmaKwXt-9KNvvg3n0joN1jqdtE7XhnW19-LQK62RgrEV6ZA
> 
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
< HTTP/2 200 
< content-type: text/event-stream
< access-control-allow-origin: *
< 
data:Hello, world Wed Aug 29 9:05:52 PDT 2022

```
```bash
$ echo '{ "data": "Hello, world '`date`'" }' | nc -c localhost 7001
```

About `20 seconds` after the JWT token was generated, when it is due to expire in `10 seconds`, a `challenge` event is sent to the client.
```bash
event:challenge
data:{"method":"POST","headers":{"content-type":"application/x-challenge-response"}}

```
When a client receives the `challenge` event, the payload indicates the `method` and `headers` to be included in the challenge-response HTTP request, along with an updated JWT token via the `authorization` header.

Note that if the client does not respond to the challenge event with an updated JWT token in time, then the SSE stream ends, ensuring that only authorized clients are allowed access.
```
* Connection #0 to host localhost left intact
```

#### Browser
Browse to `https://localhost:9090/index.html` and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click the `Go` button to attach the browser SSE event source via Zilla.

Open the browser developer tools network tab to see the EventStream for the specific events HTTP request.

The `challenge` event will show there, and the corresponding `fetch` request for the challenge response to provide the refreshed authorization token.

Note: if you uncheck the `reauthorize` checkbox, then the `challenge` event will be ignored and the event stream will end with an error event logged to the console when the JWT token expires, as expected.

### Stop SSE server and Zilla engine
```bash
$ docker stack rm example
Removing service example_zilla
Removing service example_sse
Removing network example_net0
```
