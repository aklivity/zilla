# tcp.echo
Listens on tcp ports `12345` and will echo back whatever is sent to the server.

### Requirements
 - Docker 20.10+

### Start zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
```

### Verify behavior
```bash
$ nc localhost 12345
Hello, world
Hello, world
```
