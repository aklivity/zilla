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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Base64;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttClientStateVersion;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPacketState;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class MqttClientStatesTest
{
    private static final byte[] NO_SUBSCRIPTIONS = new byte[0];
    private static final byte[] NO_OUTBOUND = new byte[0];
    private static final byte[] NO_INBOUND = new byte[0];

    private final MqttClientStates states = new MqttClientStates();

    @Test
    public void shouldDecodeAbsentValueAsEmpty()
    {
        final MqttClientStateRecord record = states.decode(null);

        assertTrue(record.packets.isEmpty());
        assertTrue(record.memberships.isEmpty());
    }

    @Test
    public void shouldRoundTripPacketsAndMemberships()
    {
        final List<MqttPacketRecord> packets = List.of(
            new MqttPacketRecord(7, MqttPacketState.PUBLISH_SENT, 2, "consumers", "sensor/one", new byte[] {1, 2, 3}),
            new MqttPacketRecord(8, MqttPacketState.PUBREL_PENDING, 2, null, null, new byte[0]));
        final List<MqttMembershipRecord> memberships = List.of(new MqttMembershipRecord("consumers", "sensor/one"));

        final MqttClientStateRecord record = states.decode(
            states.encode(memberships, packets, null, NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND));

        assertEquals(memberships, record.memberships);
        assertEquals(2, record.packets.size());
        assertEquals(7, record.packets.get(0).packetId);
        assertEquals(MqttPacketState.PUBLISH_SENT, record.packets.get(0).state);
        assertEquals("consumers", record.packets.get(0).shareName);
        assertEquals("sensor/one", record.packets.get(0).filter);
        assertArrayEquals(new byte[] {1, 2, 3}, record.packets.get(0).metadata);
        assertEquals(MqttPacketState.PUBREL_PENDING, record.packets.get(1).state);
        assertNull(record.packets.get(1).shareName);
        assertEquals(0, record.packets.get(1).metadata.length);
    }

    @Test
    public void shouldRoundTripHolder()
    {
        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), "replica-1", NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND));

        assertEquals("replica-1", record.holder);
        assertTrue(record.packets.isEmpty());
        assertTrue(record.memberships.isEmpty());
    }

    @Test
    public void shouldRoundTripReleasedAsNoHolder()
    {
        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), null, NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND));

        assertNull(record.holder);
    }

    @Test
    public void shouldDecodeVersionOneWithoutHolder()
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        final MqttClientStateFW version1 = new MqttClientStateFW.Builder()
            .wrap(buffer, 0, buffer.capacity())
            .kind(MqttClientStateVersion.V1)
            .clientStateV1(v1 -> v1.membershipsItem(m -> m.shareName("consumers").filter("sensor/one")))
            .build();
        final byte[] bytes = new byte[version1.sizeof()];
        buffer.getBytes(version1.offset(), bytes);

        final MqttClientStateRecord record = states.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));

        assertEquals(List.of(new MqttMembershipRecord("consumers", "sensor/one")), record.memberships);
        assertNull(record.holder);
    }

    @Test
    public void shouldRoundTripSubscriptions()
    {
        final byte[] subscriptions = {3, 0, 0, 0, 1, 7, 9, 11};

        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(new MqttMembershipRecord("consumers", "sensor/one")),
                List.of(), "replica-1", subscriptions, NO_OUTBOUND, NO_INBOUND));

        assertArrayEquals(subscriptions, record.subscriptions);
        assertEquals("replica-1", record.holder);
        assertEquals(List.of(new MqttMembershipRecord("consumers", "sensor/one")), record.memberships);
    }

    @Test
    public void shouldEncodeVersionTwoWithoutSubscriptions()
    {
        final String encoded = states.encode(List.of(), List.of(), "replica-1", NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND);

        assertEquals(MqttClientStateVersion.V2.value(), Base64.getUrlDecoder().decode(encoded)[0]);
    }

    @Test
    public void shouldEncodeVersionThreeWithSubscriptions()
    {
        final String encoded = states.encode(List.of(), List.of(), null, new byte[] {1}, NO_OUTBOUND, NO_INBOUND);

        assertEquals(MqttClientStateVersion.V3.value(), Base64.getUrlDecoder().decode(encoded)[0]);
    }

    @Test
    public void shouldDecodeVersionTwoWithoutSubscriptions()
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        final MqttClientStateFW version2 = new MqttClientStateFW.Builder()
            .wrap(buffer, 0, buffer.capacity())
            .kind(MqttClientStateVersion.V2)
            .clientStateV2(v2 -> v2.holder("replica-1"))
            .build();
        final byte[] bytes = new byte[version2.sizeof()];
        buffer.getBytes(version2.offset(), bytes);

        final MqttClientStateRecord record = states.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));

        assertEquals("replica-1", record.holder);
        assertEquals(0, record.subscriptions.length);
    }

    @Test
    public void shouldRoundTripInbound()
    {
        final byte[] inbound = {5, 0, 0, 0, 2, 9, 8, 7};

        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), "replica-1", NO_SUBSCRIPTIONS, NO_OUTBOUND, inbound));

        assertArrayEquals(inbound, record.inbound);
        assertEquals(0, record.subscriptions.length);
        assertEquals("replica-1", record.holder);
    }

    @Test
    public void shouldRoundTripInboundWithSubscriptions()
    {
        final byte[] subscriptions = {3, 0, 0, 0, 1, 7, 9, 11};
        final byte[] inbound = {5, 0, 0, 0, 2, 9, 8, 7};

        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), null, subscriptions, NO_OUTBOUND, inbound));

        assertArrayEquals(subscriptions, record.subscriptions);
        assertArrayEquals(inbound, record.inbound);
    }

    @Test
    public void shouldDecodeAbsentInboundAsEmpty()
    {
        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), "replica-1", new byte[] {1}, NO_OUTBOUND, NO_INBOUND));

        assertEquals(0, record.inbound.length);
    }

    @Test
    public void shouldRoundTripOutbound()
    {
        final byte[] subscriptions = {3, 0, 0, 0, 1, 7, 9, 11};
        final byte[] outbound = {2, 4, 6, 8, 10};

        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(new MqttMembershipRecord("consumers", "sensor/one")), List.of(), "replica-1",
                subscriptions, outbound, NO_INBOUND));

        assertArrayEquals(outbound, record.outbound);
        assertArrayEquals(subscriptions, record.subscriptions);
        assertEquals(0, record.inbound.length);
        assertEquals("replica-1", record.holder);
    }

    @Test
    public void shouldRoundTripOutboundWithoutSubscriptions()
    {
        final byte[] outbound = {2, 4, 6, 8, 10};

        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), null, NO_SUBSCRIPTIONS, outbound, NO_INBOUND));

        assertArrayEquals(outbound, record.outbound);
        assertEquals(0, record.subscriptions.length);
        assertEquals(0, record.inbound.length);
    }

    @Test
    public void shouldRoundTripOutboundWithInbound()
    {
        final byte[] subscriptions = {3, 0, 0, 0, 1, 7, 9, 11};
        final byte[] outbound = {2, 4, 6, 8, 10};
        final byte[] inbound = {5, 0, 0, 0, 2, 9, 8, 7};

        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), null, subscriptions, outbound, inbound));

        assertArrayEquals(subscriptions, record.subscriptions);
        assertArrayEquals(outbound, record.outbound);
        assertArrayEquals(inbound, record.inbound);
    }

    @Test
    public void shouldEncodeVersionThreeWithOutbound()
    {
        final String encoded = states.encode(List.of(), List.of(), null, NO_SUBSCRIPTIONS, new byte[] {1}, NO_INBOUND);

        assertEquals(MqttClientStateVersion.V3.value(), Base64.getUrlDecoder().decode(encoded)[0]);
    }

    @Test
    public void shouldDecodeAbsentOutboundAsEmpty()
    {
        final MqttClientStateRecord record = states.decode(
            states.encode(List.of(), List.of(), null, new byte[] {1}, NO_OUTBOUND, new byte[] {2}));

        assertEquals(0, record.outbound.length);
    }

    @Test
    public void shouldNotDecodeUnknownVersion()
    {
        assertNull(states.decode("AgAAAA"));
    }
}
