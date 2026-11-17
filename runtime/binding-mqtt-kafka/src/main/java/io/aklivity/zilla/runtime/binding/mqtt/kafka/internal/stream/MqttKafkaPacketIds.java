/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import org.agrona.collections.Int2ObjectHashMap;

final class MqttKafkaPacketIds<T>
{
    static final int NO_PACKET_ID = 0;
    static final int MAXIMUM_PACKET_ID = 0xFFFF;

    private final Int2ObjectHashMap<T> inflight;

    private int nextPacketId;

    MqttKafkaPacketIds()
    {
        this.inflight = new Int2ObjectHashMap<>();
        this.nextPacketId = 1;
    }

    int allocate(
        T value)
    {
        int packetId = NO_PACKET_ID;

        if (inflight.size() < MAXIMUM_PACKET_ID)
        {
            while (inflight.containsKey(nextPacketId))
            {
                nextPacketId = advance(nextPacketId);
            }

            packetId = nextPacketId;
            nextPacketId = advance(nextPacketId);
            inflight.put(packetId, value);
        }

        return packetId;
    }

    void reserve(
        int packetId,
        T value)
    {
        inflight.put(packetId, value);
    }

    T get(
        int packetId)
    {
        return inflight.get(packetId);
    }

    T release(
        int packetId)
    {
        return inflight.remove(packetId);
    }

    private static int advance(
        int packetId)
    {
        return packetId == MAXIMUM_PACKET_ID ? 1 : packetId + 1;
    }
}
