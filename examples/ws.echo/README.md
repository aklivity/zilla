# ws.echo
Listens on websocket port `8080` and will echo back whatever is sent to the server.

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

### Install wscat
```bash
$ npm install wscat -g
```

### Verify behavior
```bash
$ wscat -c ws://localhost:8080/
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```
