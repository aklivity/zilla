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

JAVA_OPTIONS="-XX:AOTCacheOutput=$aot_cache" /opt/zilla/zilla start -c /opt/zilla/zilla.training.yaml &
zilla=$!

for attempt in $(seq 1 600)
do
    [ -f "$engine_directory/ready" ] && break
    kill -0 "$zilla"
    sleep 0.1
done
[ -f "$engine_directory/ready" ]

exec 3<>/dev/tcp/127.0.0.1/7114
printf 'GET /items HTTP/1.1\r\nHost: localhost:7114\r\nConnection: close\r\n\r\n' >&3
timeout 2 cat <&3 > /dev/null || true
exec 3>&-

exec 3<>/dev/tcp/127.0.0.1/7183
printf '\x10\x12\x00\x04MQTT\x04\x02\x00\x3c\x00\x06client' >&3
timeout 2 cat <&3 > /dev/null || true
exec 3>&-

kill -TERM "$zilla"
wait "$zilla" || true

rm -rf "$engine_directory"
[ -s "$aot_cache" ]
