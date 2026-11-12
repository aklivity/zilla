#!/bin/bash
#
# Copyright 2021-2026 Aklivity Inc
#
# Licensed under the Aklivity Community License (the "License"); you may not use
# this file except in compliance with the License.  You may obtain a copy of the
# License at
#
#   https://www.aklivity.io/aklivity-community-license/
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#

set -eu

engine_directory=/var/run/zilla
aot_cache=/opt/zilla/zilla.aot

exchange()
{
    exec 3<>"/dev/tcp/127.0.0.1/$1"
    printf "$2" >&3
    timeout 2 cat <&3 > /dev/null || true
    exec 3>&-
}

JAVA_OPTIONS="-XX:AOTCacheOutput=$aot_cache" /opt/zilla/zilla start -c /opt/zilla/zilla.training.yaml &
zilla=$!

for attempt in $(seq 1 600)
do
    [ -f "$engine_directory/ready" ] && break
    kill -0 "$zilla"
    sleep 0.1
done
[ -f "$engine_directory/ready" ]

exchange 7114 'GET /items HTTP/1.1\r\nHost: localhost:7114\r\nConnection: close\r\n\r\n'
exchange 7114 'GET /echo HTTP/1.1\r\nHost: localhost:7114\r\nConnection: close\r\n\r\n'
exchange 7114 'GET /files/zilla.properties HTTP/1.1\r\nHost: localhost:7114\r\nConnection: close\r\n\r\n'
exchange 7114 'GET /ws HTTP/1.1\r\nHost: localhost:7114\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n'
exchange 7186 '\r\n\r\n\x00\r\nQUIT\n\x21\x11\x00\x0c\x7f\x00\x00\x01\x7f\x00\x00\x01\x04\xd2\x1c\x12hello\n'

kill -TERM "$zilla"
wait "$zilla" || true

rm -rf "$engine_directory"
[ -s "$aot_cache" ]
