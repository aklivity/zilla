# tcp.reflect
Listens on tcp port `12345` and will echo back whatever is sent to the server, broadcasting to all clients.

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
$ nc localhost 12345
Hello, one
Hello, one
Hello, two
```
```bash
$ nc localhost 12345
Hello, one
Hello, two
Hello, two
```

