# http.filesystem

Listens on http port `7114` and serves files from the Zilla container's `/var/www` subdirectory.

## Requirements

- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

```bash
curl http://localhost:7114/index.html
```

output:

```html
<html>
  <head>
    <title>Welcome to Zilla!</title>
  </head>
  <body>
    <h1>Welcome to Zilla!</h1>
  </body>
</html>
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
