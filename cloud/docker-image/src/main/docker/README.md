### Running locally

```bash
cat zpm.json.template | env VERSION=develop-SNAPSHOT envsubst > zpm.json
```

```bash
./zpmw clean
```

```bash
./zpmw install --debug --exclude-remote-repositories  
```

```
./zilla start --config <path/to/zilla.yaml>
```
