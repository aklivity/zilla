# http.echo
Listens on http port `8080` and will echo back whatever is sent to the server.
Listens on https port `9090` and will echo back whatever is sent to the server.

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
$ curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/
Hello, world
```
```bash
$ curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/ --http2-prior-knowledge
Hello, world
```
```bash
$ curl --cacert test-ca.crt -d "Hello, world" -H "Content-Type: text/plain" -X "POST" https://localhost:9090/ --http1.1
Hello, world
```
```bash
$ curl --cacert test-ca.crt -d "Hello, world" -H "Content-Type: text/plain" -X "POST" https://localhost:9090/ --http2
Hello, world
```
