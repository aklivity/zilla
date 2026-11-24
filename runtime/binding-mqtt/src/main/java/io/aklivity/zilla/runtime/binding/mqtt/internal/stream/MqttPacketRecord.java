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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPacketState;

final class MqttPacketRecord
{
    final int packetId;
    final MqttPacketState state;
    final int qos;
    final String shareName;
    final String filter;
    final byte[] metadata;

    MqttPacketRecord(
        int packetId,
        MqttPacketState state,
        int qos,
        String shareName,
        String filter,
        byte[] metadata)
    {
        this.packetId = packetId;
        this.state = state;
        this.qos = qos;
        this.shareName = shareName;
        this.filter = filter;
        this.metadata = metadata;
    }

    MqttPacketRecord pubrelPending()
    {
        return new MqttPacketRecord(packetId, MqttPacketState.PUBREL_PENDING, qos, shareName, filter, metadata);
    }
}
