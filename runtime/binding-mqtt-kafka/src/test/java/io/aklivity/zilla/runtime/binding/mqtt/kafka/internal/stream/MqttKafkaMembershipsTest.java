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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Base64;
import java.util.List;

import org.agrona.BitUtil;
import org.junit.Test;

public class MqttKafkaMembershipsTest
{
    private final MqttKafkaMemberships memberships = new MqttKafkaMemberships();

    @Test
    public void shouldEncodeEmptyList()
    {
        assertEquals("AQQAAAAAAAAA", memberships.encode(List.of()));
    }

    @Test
    public void shouldEncodeSingleMembership()
    {
        final String encoded = memberships.encode(List.of(new MqttKafkaMembership("consumers", "sensor/one")));

        assertEquals(
            "01" +
            "1b000000" + "01000000" +
            "0900" + "636f6e73756d657273" + "0a00" + "73656e736f722f6f6e65",
            hex(encoded));
    }

    @Test
    public void shouldEncodeMembershipsInOrder()
    {
        final String encoded = memberships.encode(List.of(
            new MqttKafkaMembership("g", "a/#"),
            new MqttKafkaMembership("h", "b")));

        assertEquals(
            "01" +
            "12000000" + "02000000" +
            "0100" + "67" + "0300" + "612f23" +
            "0100" + "68" + "0100" + "62",
            hex(encoded));
    }

    @Test
    public void shouldDecodeEncodedMemberships()
    {
        final List<MqttKafkaMembership> expected = List.of(
            new MqttKafkaMembership("consumers", "sensor/#"),
            new MqttKafkaMembership("others", "+/one"));

        assertEquals(expected, memberships.decode(memberships.encode(expected)));
    }

    @Test
    public void shouldDecodeEmptyList()
    {
        assertTrue(memberships.decode(memberships.encode(List.of())).isEmpty());
    }

    @Test
    public void shouldDecodeAbsentValueAsNoMemberships()
    {
        assertTrue(memberships.decode(null).isEmpty());
    }

    @Test
    public void shouldNotDecodeUnknownVersion()
    {
        final String unknown = Base64.getUrlEncoder().withoutPadding().encodeToString(BitUtil.fromHex("0204000000000000"));

        assertNull(memberships.decode(unknown));
    }

    @Test
    public void shouldNotDecodeTruncatedMemberships()
    {
        final String truncated = Base64.getUrlEncoder().withoutPadding().encodeToString(BitUtil.fromHex("011b0000"));

        assertNull(memberships.decode(truncated));
    }

    @Test
    public void shouldMatchSubscriptionOfMembership()
    {
        final MqttKafkaMembership membership = new MqttKafkaMembership("consumers", "sensor/one");

        assertTrue(membership.subscribed("consumers", "sensor/one"));
        assertFalse(membership.subscribed("others", "sensor/one"));
        assertFalse(membership.subscribed("consumers", "sensor/two"));
    }

    @Test
    public void shouldEqualMembershipOfSameSubscription()
    {
        assertEquals(new MqttKafkaMembership("consumers", "sensor/one"), new MqttKafkaMembership("consumers", "sensor/one"));
        assertEquals(new MqttKafkaMembership("consumers", "sensor/one").hashCode(),
            new MqttKafkaMembership("consumers", "sensor/one").hashCode());
        assertNotEquals(new MqttKafkaMembership("consumers", "sensor/one"), new MqttKafkaMembership("consumers", "sensor/two"));
    }

    private static String hex(
        String encoded)
    {
        return BitUtil.toHex(Base64.getUrlDecoder().decode(encoded)).toLowerCase();
    }
}
