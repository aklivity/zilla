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
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateV2FW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateV3FW;
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
    private static final byte[] NO_SUBSCRIPTIONS = new byte[0];
    private static final byte[] NO_OUTBOUND = new byte[0];
    private static final int OUTBOUND_FIXED_SIZE = 8;
    private static final int SUBSCRIPTIONS_FIXED_SIZE = 8;
    private static final int HEADER_SIZE = 16;
    private static final int HOLDER_FIXED_SIZE = 8;
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
        List<MqttPacketRecord> packets,
        String holder,
        byte[] subscriptions,
        byte[] outbound)
    {
        final int capacity = capacity(memberships, packets, holder, subscriptions, outbound);

        if (encodeBuffer.capacity() < capacity)
        {
            encodeBuffer = new UnsafeBufferEx(new byte[capacity]);
        }

        final MqttClientStateFW.Builder builder = clientStateRW.wrap(encodeBuffer, 0, encodeBuffer.capacity());
        final MqttClientStateFW encoded = subscriptions.length == 0 && outbound.length == 0
            ? builder.kind(MqttClientStateVersion.V2)
                .clientStateV2(v2 -> encodeV2(v2, memberships, packets, holder))
                .build()
            : builder.kind(MqttClientStateVersion.V3)
                .clientStateV3(v3 -> encodeV3(v3, memberships, packets, holder, subscriptions, outbound))
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
        String holder = null;
        byte[] subscriptions = NO_SUBSCRIPTIONS;
        byte[] outbound = NO_OUTBOUND;
        boolean decodable = true;

        if (value != null)
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(decoder.decode(value));
            final MqttClientStateVersion version = buffer.capacity() > 0
                ? MqttClientStateVersion.valueOf(buffer.getByte(0) & 0xFF)
                : null;
            final MqttClientStateFW decoded = version == MqttClientStateVersion.V1 || version == MqttClientStateVersion.V2 ||
                version == MqttClientStateVersion.V3
                ? clientStateRO.tryWrap(buffer, 0, buffer.capacity())
                : null;

            decodable = decoded != null;

            if (decodable && version == MqttClientStateVersion.V1)
            {
                final MqttClientStateV1FW v1 = decoded.clientStateV1();
                v1.memberships().forEach(m -> memberships.add(newMembership(m)));
                v1.packets().forEach(p -> packets.add(newPacket(p)));
            }
            else if (decodable && version == MqttClientStateVersion.V3)
            {
                final MqttClientStateV3FW v3 = decoded.clientStateV3();
                v3.memberships().forEach(m -> memberships.add(newMembership(m)));
                v3.packets().forEach(p -> packets.add(newPacket(p)));
                holder = v3.holder().asString();
                subscriptions = newBinary(v3.subscriptions().bytes());
                outbound = newBinary(v3.outbound().bytes());
            }
            else if (decodable)
            {
                final MqttClientStateV2FW v2 = decoded.clientStateV2();
                v2.memberships().forEach(m -> memberships.add(newMembership(m)));
                v2.packets().forEach(p -> packets.add(newPacket(p)));
                holder = v2.holder().asString();
            }
        }

        return decodable ? new MqttClientStateRecord(memberships, packets, holder, subscriptions, outbound) : null;
    }

    private static void encodeV3(
        MqttClientStateV3FW.Builder v3,
        List<MqttMembershipRecord> memberships,
        List<MqttPacketRecord> packets,
        String holder,
        byte[] subscriptions,
        byte[] outbound)
    {
        memberships.forEach(m -> v3.membershipsItem(i -> i
            .shareName(m.shareName)
            .filter(m.filter)));
        packets.forEach(p -> v3.packetsItem(i -> i
            .packetId(p.packetId)
            .state(p.state.value())
            .qos(p.qos)
            .shareName(p.shareName)
            .filter(p.filter)
            .metadata(m -> m.bytes(b -> b.set(p.metadata)))));
        v3.holder(holder);
        v3.subscriptions(s -> s.bytes(b -> b.set(subscriptions)));
        v3.outbound(o -> o.bytes(b -> b.set(outbound)));
    }

    private static void encodeV2(
        MqttClientStateV2FW.Builder v2,
        List<MqttMembershipRecord> memberships,
        List<MqttPacketRecord> packets,
        String holder)
    {
        memberships.forEach(m -> v2.membershipsItem(i -> i
            .shareName(m.shareName)
            .filter(m.filter)));
        packets.forEach(p -> v2.packetsItem(i -> i
            .packetId(p.packetId)
            .state(p.state.value())
            .qos(p.qos)
            .shareName(p.shareName)
            .filter(p.filter)
            .metadata(m -> m.bytes(b -> b.set(p.metadata)))));
        v2.holder(holder);
    }

    private static byte[] newBinary(
        OctetsFW bytes)
    {
        final byte[] binary = bytes != null ? new byte[bytes.sizeof()] : NO_METADATA;

        if (bytes != null)
        {
            bytes.buffer().getBytes(bytes.offset(), binary);
        }

        return binary;
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
        List<MqttPacketRecord> packets,
        String holder,
        byte[] subscriptions,
        byte[] outbound)
    {
        int capacity = HEADER_SIZE + HOLDER_FIXED_SIZE + length(holder) * MAXIMUM_UTF8_BYTES_PER_CHAR +
            SUBSCRIPTIONS_FIXED_SIZE + subscriptions.length + OUTBOUND_FIXED_SIZE + outbound.length;

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
