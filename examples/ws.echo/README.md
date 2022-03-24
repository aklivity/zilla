# ws.echo
Listens on ws port `8080` and will echo back whatever is sent to the server.
Listens on wss port `9090` and will echo back whatever is sent to the server.

### Requirements
 - Docker 20.10+

### Start zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
```

### Install wscat
```bash
$ npm install wscat -g
```

### Verify behavior
```bash
$ wscat -c ws://localhost:8080/ -s echo
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```
```bash
$ wscat -c wss://localhost:9090/ --ca test-ca.crt -s echo
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```
