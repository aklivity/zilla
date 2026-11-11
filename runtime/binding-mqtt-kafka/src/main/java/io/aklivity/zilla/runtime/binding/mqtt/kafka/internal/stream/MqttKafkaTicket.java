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

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketState;

final class MqttKafkaTicket
{
    final int packetId;
    final MqttSharedTicketState state;
    final String shareName;
    final String filter;
    final String topic;
    final int partitionId;
    final long partitionOffset;

    MqttKafkaTicket(
        int packetId,
        MqttSharedTicketState state,
        String shareName,
        String filter,
        String topic,
        int partitionId,
        long partitionOffset)
    {
        this.packetId = packetId;
        this.state = state;
        this.shareName = shareName;
        this.filter = filter;
        this.topic = topic;
        this.partitionId = partitionId;
        this.partitionOffset = partitionOffset;
    }

    MqttKafkaTicket pubrelPending()
    {
        return new MqttKafkaTicket(packetId, MqttSharedTicketState.PUBREL_PENDING, shareName, filter, topic,
            partitionId, partitionOffset);
    }

    boolean subscribed(
        String shareName,
        String filter)
    {
        return this.shareName.equals(shareName) && this.filter.equals(filter);
    }
}
