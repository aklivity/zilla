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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketsVersion;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class MqttKafkaTickets
{
    private static final int HEADER_SIZE = 16;
    private static final int TICKET_FIXED_SIZE = 40;
    private static final int MAXIMUM_UTF8_BYTES_PER_CHAR = 3;

    private final MqttSharedTicketsFW.Builder ticketsRW = new MqttSharedTicketsFW.Builder();
    private final MqttSharedTicketsFW ticketsRO = new MqttSharedTicketsFW();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    private MutableDirectBufferEx encodeBuffer = new UnsafeBufferEx(new byte[0]);

    String encode(
        List<MqttKafkaTicket> tickets)
    {
        final int capacity = capacity(tickets);

        if (encodeBuffer.capacity() < capacity)
        {
            encodeBuffer = new UnsafeBufferEx(new byte[capacity]);
        }

        final MqttSharedTicketsFW encoded = ticketsRW.wrap(encodeBuffer, 0, encodeBuffer.capacity())
            .kind(MqttSharedTicketsVersion.V1)
            .ticketsV1(v1 -> tickets.forEach(t -> v1.ticketsItem(i -> i
                .packetId(t.packetId)
                .state(s -> s.set(t.state))
                .shareName(t.shareName)
                .filter(t.filter)
                .topic(t.topic)
                .partitionId(t.partitionId)
                .partitionOffset(t.partitionOffset))))
            .build();

        final byte[] bytes = new byte[encoded.sizeof()];
        encodeBuffer.getBytes(encoded.offset(), bytes);

        return encoder.encodeToString(bytes);
    }

    List<MqttKafkaTicket> decode(
        String value)
    {
        final List<MqttKafkaTicket> tickets = new ArrayList<>();
        boolean decodable = true;

        if (value != null)
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(decoder.decode(value));
            final MqttSharedTicketsVersion version = buffer.capacity() > 0
                ? MqttSharedTicketsVersion.valueOf(buffer.getByte(0) & 0xFF)
                : null;
            final MqttSharedTicketsFW decoded = version == MqttSharedTicketsVersion.V1
                ? ticketsRO.tryWrap(buffer, 0, buffer.capacity())
                : null;

            decodable = decoded != null;

            if (decodable)
            {
                decoded.ticketsV1().tickets().forEach(t -> tickets.add(newTicket(t)));
            }
        }

        return decodable ? tickets : null;
    }

    private static MqttKafkaTicket newTicket(
        MqttSharedTicketFW ticket)
    {
        return new MqttKafkaTicket(
            ticket.packetId(),
            ticket.state().get(),
            ticket.shareName().asString(),
            ticket.filter().asString(),
            ticket.topic().asString(),
            ticket.partitionId(),
            ticket.partitionOffset());
    }

    private static int capacity(
        List<MqttKafkaTicket> tickets)
    {
        int capacity = HEADER_SIZE;

        for (int i = 0; i < tickets.size(); i++)
        {
            final MqttKafkaTicket ticket = tickets.get(i);
            capacity += TICKET_FIXED_SIZE +
                (ticket.shareName.length() + ticket.filter.length() + ticket.topic.length()) * MAXIMUM_UTF8_BYTES_PER_CHAR;
        }

        return capacity;
    }
}
