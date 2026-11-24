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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateV1FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateVersion;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttMembershipFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPacketFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPacketState;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class MqttClientStates
{
    private static final byte[] NO_METADATA = new byte[0];
    private static final int HEADER_SIZE = 16;
    private static final int MEMBERSHIP_FIXED_SIZE = 8;
    private static final int PACKET_FIXED_SIZE = 32;
    private static final int MAXIMUM_UTF8_BYTES_PER_CHAR = 3;

    private final MqttClientStateFW.Builder clientStateRW = new MqttClientStateFW.Builder();
    private final MqttClientStateFW clientStateRO = new MqttClientStateFW();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    private MutableDirectBufferEx encodeBuffer = new UnsafeBufferEx(new byte[0]);

    String encode(
        List<MqttMembershipRecord> memberships,
        List<MqttPacketRecord> packets)
    {
        final int capacity = capacity(memberships, packets);

        if (encodeBuffer.capacity() < capacity)
        {
            encodeBuffer = new UnsafeBufferEx(new byte[capacity]);
        }

        final MqttClientStateFW encoded = clientStateRW.wrap(encodeBuffer, 0, encodeBuffer.capacity())
            .kind(MqttClientStateVersion.V1)
            .clientStateV1(v1 -> encodeV1(v1, memberships, packets))
            .build();

        final byte[] bytes = new byte[encoded.sizeof()];
        encodeBuffer.getBytes(encoded.offset(), bytes);

        return encoder.encodeToString(bytes);
    }

    MqttClientStateRecord decode(
        String value)
    {
        final List<MqttMembershipRecord> memberships = new ArrayList<>();
        final List<MqttPacketRecord> packets = new ArrayList<>();
        boolean decodable = true;

        if (value != null)
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(decoder.decode(value));
            final MqttClientStateVersion version = buffer.capacity() > 0
                ? MqttClientStateVersion.valueOf(buffer.getByte(0) & 0xFF)
                : null;
            final MqttClientStateFW decoded = version == MqttClientStateVersion.V1
                ? clientStateRO.tryWrap(buffer, 0, buffer.capacity())
                : null;

            decodable = decoded != null;

            if (decodable)
            {
                final MqttClientStateV1FW v1 = decoded.clientStateV1();
                v1.memberships().forEach(m -> memberships.add(newMembership(m)));
                v1.packets().forEach(p -> packets.add(newPacket(p)));
            }
        }

        return decodable ? new MqttClientStateRecord(memberships, packets) : null;
    }

    private static void encodeV1(
        MqttClientStateV1FW.Builder v1,
        List<MqttMembershipRecord> memberships,
        List<MqttPacketRecord> packets)
    {
        memberships.forEach(m -> v1.membershipsItem(i -> i
            .shareName(m.shareName)
            .filter(m.filter)));
        packets.forEach(p -> v1.packetsItem(i -> i
            .packetId(p.packetId)
            .state(p.state.value())
            .qos(p.qos)
            .shareName(p.shareName)
            .filter(p.filter)
            .metadata(m -> m.bytes(b -> b.set(p.metadata)))));
    }

    private static MqttMembershipRecord newMembership(
        MqttMembershipFW membership)
    {
        return new MqttMembershipRecord(membership.shareName().asString(), membership.filter().asString());
    }

    private static MqttPacketRecord newPacket(
        MqttPacketFW packet)
    {
        final OctetsFW bytes = packet.metadata().bytes();
        final byte[] metadata = bytes != null ? new byte[bytes.sizeof()] : NO_METADATA;

        if (bytes != null)
        {
            bytes.buffer().getBytes(bytes.offset(), metadata);
        }

        return new MqttPacketRecord(
            packet.packetId(),
            MqttPacketState.valueOf(packet.state()),
            packet.qos(),
            packet.shareName().asString(),
            packet.filter().asString(),
            metadata);
    }

    private static int capacity(
        List<MqttMembershipRecord> memberships,
        List<MqttPacketRecord> packets)
    {
        int capacity = HEADER_SIZE;

        for (int i = 0; i < memberships.size(); i++)
        {
            final MqttMembershipRecord membership = memberships.get(i);
            capacity += MEMBERSHIP_FIXED_SIZE +
                (membership.shareName.length() + membership.filter.length()) * MAXIMUM_UTF8_BYTES_PER_CHAR;
        }

        for (int i = 0; i < packets.size(); i++)
        {
            final MqttPacketRecord packet = packets.get(i);
            capacity += PACKET_FIXED_SIZE + packet.metadata.length +
                (length(packet.shareName) + length(packet.filter)) * MAXIMUM_UTF8_BYTES_PER_CHAR;
        }

        return capacity;
    }

    private static int length(
        String value)
    {
        return value != null ? value.length() : 0;
    }
}
