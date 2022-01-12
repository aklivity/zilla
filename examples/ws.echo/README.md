# ws.echo
Listens on ws port `8080` and will echo back whatever is sent to the server.
Listens on wss port `9090` and will echo back whatever is sent to the server.

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
