# ws.reflect
Listens on websocket port `8080` and will echo back whatever is sent to the server, broadcasting to all clients.

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
Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.
```bash
$ wscat -c ws://localhost:8080/
Connected (press CTRL+C to quit)
> Hello, one
< Hello, one
< Hello, two
```
```bash
$ wscat -c ws://localhost:8080/
Connected (press CTRL+C to quit)
< Hello, one
> Hello, two
< Hello, two
```
