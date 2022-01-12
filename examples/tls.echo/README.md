# tls.echo
Listens on tls port `23456` and will echo back whatever is sent to the server.

### Requirements
 - JDK 11 or higher.

### Install modular Java runtime
```bash
$ ./zpmw clean
$ ./zpmw install --exclude-remote-repositories
...
linked modules
generated launcher
```

### Start zilla engine
```bash
$ ./zilla start
started
```

### Verify behavior
```bash
$ openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
Hello, world
Hello, world
```
