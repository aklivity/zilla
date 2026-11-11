/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tcp.internal.bench;

/**
 * Direction of the bytes a TCP benchmark moves, from the point of view of the engine: {@code INBOUND} bytes
 * arrive from the socket and leave to the application stream, {@code OUTBOUND} bytes arrive from the
 * application stream and leave to the socket, and {@code ECHO} bytes make the round trip.
 */
public enum TcpBenchDirection
{
    INBOUND,
    OUTBOUND,
    ECHO
}
