The `dump` command creates a `pcap` file that can be opened by Wireshark using the `zilla.lua` dissector plugin.

`WiresharkIT` is an integration test that tests the `zilla.lua` dissector by running `tshark` in a docker container. If it doesn't find the image, it builds it on-the-fly, but the process is faster if the `tshark` image is pre-built.

This is the command to build a multi-arch `tshark` image and push it to a docker repository:

```bash
cd <zilla-source>/incubator/command-dump/src/test/resources/io/aklivity/zilla/runtime/command/dump/internal
docker buildx create --name container --driver=docker-container
docker buildx build --tag <repository>/tshark:<version> --platform linux/arm64/v8,linux/amd64 --builder container --push .
```
