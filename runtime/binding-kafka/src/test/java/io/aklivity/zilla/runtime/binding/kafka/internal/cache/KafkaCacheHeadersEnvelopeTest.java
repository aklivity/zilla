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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCacheHeadersEnvelopeTest
{
    @Test
    public void shouldPresentEmptyEnvelopeBeforeWrap()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();

        assertEquals(0, envelope.count("trace"));
        assertNull(envelope.get("trace", 0));
    }

    @Test
    public void shouldCountZeroForAbsentName()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(headers("trace", "one"), empty());

        assertEquals(0, envelope.count("absent"));
        assertNull(envelope.get("absent", 0));
    }

    @Test
    public void shouldReadValueFromHeaders()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(headers("trace", "one"), empty());

        assertEquals(1, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
    }

    @Test
    public void shouldReadValueFromTrailersWhenAbsentFromHeaders()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(empty(), headers("trace", "one"));

        assertEquals(1, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
    }

    @Test
    public void shouldReadRepeatedValuesInOrderAcrossHeadersThenTrailers()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(headers("trace", "one", "trace", "two"), headers("trace", "three"));

        assertEquals(3, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
        assertEquals("two", text(envelope.get("trace", 1)));
        assertEquals("three", text(envelope.get("trace", 2)));
    }

    @Test
    public void shouldReturnNullForOutOfRangeIndex()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(headers("trace", "one"), empty());

        assertNull(envelope.get("trace", 1));
    }

    @Test
    public void shouldNotConfuseDifferentNames()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(headers("trace", "one", "span", "two"), empty());

        assertEquals(1, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
        assertEquals(1, envelope.count("span"));
        assertEquals("two", text(envelope.get("span", 0)));
    }

    @Test
    public void shouldDiscardWrites()
    {
        KafkaCacheHeadersEnvelope envelope = new KafkaCacheHeadersEnvelope();
        envelope.wrap(headers("trace", "one"), empty());

        envelope.set("trace", buffer("two"));

        assertEquals(1, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
    }

    private Array32FW<KafkaHeaderFW> empty()
    {
        MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(256));
        return new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
            .wrap(writeBuffer, 0, writeBuffer.capacity())
            .build();
    }

    private Array32FW<KafkaHeaderFW> headers(
        String... nameValuePairs)
    {
        MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(256));
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(writeBuffer, 0, writeBuffer.capacity());

        for (int i = 0; i < nameValuePairs.length; i += 2)
        {
            byte[] name = nameValuePairs[i].getBytes(UTF_8);
            byte[] value = nameValuePairs[i + 1].getBytes(UTF_8);
            builder.item(h -> h.nameLen(name.length).name(n -> n.set(name))
                .valueLen(value.length).value(v -> v.set(value)));
        }

        return builder.build();
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }
}
