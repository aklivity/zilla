# ws.reflect
Listens on ws port `8080` and will echo back whatever is sent to the server, broadcasting to all clients.
Listens on wss port `9090` and will echo back whatever is sent to the server, broadcasting to all clients.

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
Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.
```bash
$ wscat -c ws://localhost:8080/ -s echo
Connected (press CTRL+C to quit)
> Hello, one
< Hello, one
< Hello, two
```
```bash
$ wscat -c wss://localhost:9090/ --ca test-ca.crt -s echo
Connected (press CTRL+C to quit)
< Hello, one
> Hello, two
< Hello, two
```
