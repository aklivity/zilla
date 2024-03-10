# openapi.proxy
## Running locally

This example runs using Docker compose. You will find the setup scripts in the [compose](./docker/compose) folder.

### Setup

The `setup.sh` script will:

- Configured Zilla instance
- Start openapi-mock

```bash
./compose/setup.sh
```

### Test

```bash
curl --location 'http://localhost:7114/pets' --header 'Accept: application/json'
```

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./compose/teardown.sh
```
