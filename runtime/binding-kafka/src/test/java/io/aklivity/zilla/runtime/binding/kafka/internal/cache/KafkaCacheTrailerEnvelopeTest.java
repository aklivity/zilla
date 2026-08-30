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
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCacheTrailerEnvelopeTest
{
    @Test
    public void shouldStartEmpty()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();

        assertTrue(envelope.isEmpty());
        assertEquals(0, envelope.count("trace"));
        assertNull(envelope.get("trace", 0));
    }

    @Test
    public void shouldAccumulateRepeatedValuesUnderOneName()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();

        envelope.set("trace", buffer("one"));
        envelope.set("trace", buffer("two"));

        assertTrue(!envelope.isEmpty());
        assertEquals(2, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
        assertEquals("two", text(envelope.get("trace", 1)));
    }

    @Test
    public void shouldNotConfuseDifferentNames()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();

        envelope.set("trace", buffer("one"));
        envelope.set("span", buffer("two"));

        assertEquals(1, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
        assertEquals(1, envelope.count("span"));
        assertEquals("two", text(envelope.get("span", 0)));
    }

    @Test
    public void shouldCopyValueBytesOnSet()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        MutableDirectBufferEx value = new UnsafeBufferEx("one".getBytes(UTF_8));

        envelope.set("trace", value);
        value.putByte(0, (byte) 'X');

        assertEquals("one", text(envelope.get("trace", 0)));
    }

    @Test
    public void shouldResetClearAccumulatedValues()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        envelope.set("trace", buffer("one"));

        envelope.reset();

        assertTrue(envelope.isEmpty());
        assertEquals(0, envelope.count("trace"));
        assertNull(envelope.get("trace", 0));
    }

    @Test
    public void shouldWriteNothingWhenEmpty()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(256));
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(writeBuffer, 0, writeBuffer.capacity());

        envelope.writeHeaders(builder);
        Array32FW<KafkaHeaderFW> headers = builder.build();

        assertEquals(0, headers.fieldCount());
    }

    @Test
    public void shouldWriteAccumulatedEntriesAsHeadersInOrder()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        envelope.set("trace", buffer("one"));
        envelope.set("trace", buffer("two"));
        envelope.set("span", buffer("three"));

        MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(256));
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(writeBuffer, 0, writeBuffer.capacity());

        envelope.writeHeaders(builder);
        Array32FW<KafkaHeaderFW> headers = builder.build();

        assertEquals(3, headers.fieldCount());
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        headers.forEach(h ->
        {
            names.add(text(h.name().value()));
            values.add(text(h.value().value()));
        });
        assertEquals(List.of("trace", "trace", "span"), names);
        assertEquals(List.of("one", "two", "three"), values);
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
