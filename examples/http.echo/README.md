# http.echo
Listens on http port `8080` and will echo back whatever is sent to the server.

### Requirements
 - JDK 11 or higher.

### Install modular Java runtime
```bash
$ ./zpmw install
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
