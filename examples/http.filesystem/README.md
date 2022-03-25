# http.filesystem
Listens on http port `8080` and serves files from `web` subdirectory.
Listens on https port `9090` and serves files from `web` subdirectory.

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
$ curl --cacert test-ca.crt https://localhost:9090/index.html
<html>
<head>
<title>Welcome to Zilla!</title>
</head>
<body>
<h1>Welcome to Zilla!</h1>
</body>
</html>
```
