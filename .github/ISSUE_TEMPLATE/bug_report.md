---
name: Bug report
about: Create a report to help us improve
title: ''
labels: 'bug'
assignees: ''

---

<!--
  - Please search to see if an issue already exists for the issue you encountered. Duplicate issues will be closed. [Current GitHub Issues](https://github.com/aklivity/zilla/issues)
  - Please make sure your Zilla version is up to date. [Latest Zilla Release](https://github.com/aklivity/zilla/releases/latest)
-->

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Zilla Environment:**
<!--
Describe the Host environment including:
- Zilla start command
- Environment variables

Describe a docker container:
```
docker inspect <zilla container name> | jq -r '.[].Config' 
```

Describe a k8s pod:
```
kubectl describe pod <zilla pod name prefix> -n <zilla k8s namespace>
```
-->

**Attach the `zilla.yaml` config file:**

**Attach the `zilla dump` pcap file:**
<!--
This command will need to be executed inside the running container or where zilla is installed.
docs: https://docs.aklivity.io/zilla/latest/reference/config/zilla-cli.html#zilla-dump 
-->
```
ZILLA_INCUBATOR_ENABLED=true ./zilla dump --verbose --write /tmp/zilla.pcap
```

**Kafka Environment:**
<!-- If applicable, please complete the following information and any other relevant details -->
- Provider: [e.g. Kafka, Confluent, Redpanda]
- Version: [e.g. 22]
- Config: [e.g. log compaction, Sasl]

**Client Environment:**
<!-- If applicable, please complete the following information and any other relevant details -->
 - Service: [e.g. IoT, Microservice, REST client]
 - Library/SDK:
 - Browser:
 - Version:

**Additional context**
Add any other context about the problem here.
