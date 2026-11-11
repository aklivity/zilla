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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketState.PUBLISH_SENT;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketState.PUBREL_PENDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Base64;
import java.util.List;

import org.agrona.BitUtil;
import org.junit.Test;

public class MqttKafkaTicketsTest
{
    private final MqttKafkaTickets tickets = new MqttKafkaTickets();

    @Test
    public void shouldEncodeEmptyList()
    {
        assertEquals("AQQAAAAAAAAA", tickets.encode(List.of()));
    }

    @Test
    public void shouldEncodeSingleTicket()
    {
        final String encoded = tickets.encode(List.of(
            new MqttKafkaTicket(1, PUBLISH_SENT, "consumers", "sensor/one", "mqtt-messages", 0, 5L)));

        assertEquals(
            "01" +
            "39000000" + "01000000" +
            "0100" + "01" + "0900" + "636f6e73756d657273" + "0a00" + "73656e736f722f6f6e65" +
            "0d00" + "6d7174742d6d657373616765" + "73" +
            "00000000" +
            "0500000000000000",
            hex(encoded));
    }

    @Test
    public void shouldEncodeTicketsInOrder()
    {
        final String encoded = tickets.encode(List.of(
            new MqttKafkaTicket(1, PUBREL_PENDING, "g", "a/#", "t", 2, 4294967296L),
            new MqttKafkaTicket(65535, PUBLISH_SENT, "h", "b", "ab", -1, 7L)));

        assertEquals(
            "01" +
            "37000000" + "02000000" +
            "0100" + "02" + "0100" + "67" + "0300" + "612f23" + "0100" + "74" + "02000000" + "0000000001000000" +
            "ffff" + "01" + "0100" + "68" + "0100" + "62" + "0200" + "6162" + "ffffffff" + "0700000000000000",
            hex(encoded));
    }

    @Test
    public void shouldDecodeEncodedTickets()
    {
        final List<MqttKafkaTicket> expected = List.of(
            new MqttKafkaTicket(1, PUBREL_PENDING, "consumers", "sensor/#", "mqtt-messages", 2, 4294967296L),
            new MqttKafkaTicket(65535, PUBLISH_SENT, "others", "+/one", "other/topic", -1, 7L));

        final List<MqttKafkaTicket> actual = tickets.decode(tickets.encode(expected));

        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++)
        {
            assertEquals(expected.get(i).packetId, actual.get(i).packetId);
            assertEquals(expected.get(i).state, actual.get(i).state);
            assertEquals(expected.get(i).shareName, actual.get(i).shareName);
            assertEquals(expected.get(i).filter, actual.get(i).filter);
            assertEquals(expected.get(i).topic, actual.get(i).topic);
            assertEquals(expected.get(i).partitionId, actual.get(i).partitionId);
            assertEquals(expected.get(i).partitionOffset, actual.get(i).partitionOffset);
        }
    }

    @Test
    public void shouldDecodeEmptyList()
    {
        assertTrue(tickets.decode(tickets.encode(List.of())).isEmpty());
    }

    @Test
    public void shouldDecodeAbsentValueAsNoTickets()
    {
        assertTrue(tickets.decode(null).isEmpty());
    }

    @Test
    public void shouldNotDecodeUnknownVersion()
    {
        final String unknown = Base64.getUrlEncoder().withoutPadding().encodeToString(BitUtil.fromHex("0208000000000000"));

        assertNull(tickets.decode(unknown));
    }

    @Test
    public void shouldMarkTicketPubrelPending()
    {
        final MqttKafkaTicket sent = new MqttKafkaTicket(3, PUBLISH_SENT, "consumers", "sensor/one", "mqtt-messages", 1, 9L);

        final MqttKafkaTicket pending = sent.pubrelPending();

        assertEquals(PUBREL_PENDING, pending.state);
        assertEquals(3, pending.packetId);
        assertEquals("consumers", pending.shareName);
        assertEquals("sensor/one", pending.filter);
        assertEquals("mqtt-messages", pending.topic);
        assertEquals(1, pending.partitionId);
        assertEquals(9L, pending.partitionOffset);
    }

    @Test
    public void shouldMatchSubscriptionOfTicket()
    {
        final MqttKafkaTicket ticket = new MqttKafkaTicket(3, PUBLISH_SENT, "consumers", "sensor/one", "mqtt-messages", 1, 9L);

        assertTrue(ticket.subscribed("consumers", "sensor/one"));
        assertFalse(ticket.subscribed("others", "sensor/one"));
        assertFalse(ticket.subscribed("consumers", "sensor/two"));
    }

    private static String hex(
        String encoded)
    {
        return BitUtil.toHex(Base64.getUrlDecoder().decode(encoded)).toLowerCase();
    }
}
