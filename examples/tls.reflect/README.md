# tls.reflect
Listens on tls port `23456` and will echo back whatever is sent to the server, broadcasting to all clients.

### Requirements
 - Docker 20.10+

### Start zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
```

### Verify behavior
Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.
```bash
$ openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
Hello, one
Hello, one
Hello, two
```
```bash
$ openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
Hello, one
Hello, two
Hello, two
```
