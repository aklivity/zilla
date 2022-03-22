# http.filesystem
Listens on http port `8080` and serves files from `web` subdirectory.
Listens on https port `9090` and serves files from `web` subdirectory.

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
$ curl http://localhost:8080/index.html
<html>
<head>
<title>Welcome to Zilla!</title>
</head>
<body>
<h1>Welcome to Zilla!</h1>
</body>
</html>
```
```bash
$ curl --cacert test-ca.crt https://localhost:9090//index.html
<html>
<head>
<title>Welcome to Zilla!</title>
</head>
<body>
<h1>Welcome to Zilla!</h1>
</body>
</html>
```
