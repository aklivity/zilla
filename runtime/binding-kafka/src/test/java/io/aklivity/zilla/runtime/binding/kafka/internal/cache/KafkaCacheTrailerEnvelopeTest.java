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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCacheTrailerEnvelopeTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldStartEmpty() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);

            assertTrue(envelope.isEmpty());
            assertFalse(envelope.isOverflowed());
            assertEquals(0, envelope.count("trace"));
            assertNull(envelope.get("trace", 0));
        }
    }

    @Test
    public void shouldAccumulateRepeatedValuesUnderOneName() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);

            envelope.set("trace", buffer("one"));
            envelope.set("trace", buffer("two"));

            assertTrue(!envelope.isEmpty());
            assertEquals(2, envelope.count("trace"));
            assertEquals("one", text(envelope.get("trace", 0)));
            assertEquals("two", text(envelope.get("trace", 1)));
        }
    }

    @Test
    public void shouldNotConfuseDifferentNames() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);

            envelope.set("trace", buffer("one"));
            envelope.set("span", buffer("two"));

            assertEquals(1, envelope.count("trace"));
            assertEquals("one", text(envelope.get("trace", 0)));
            assertEquals(1, envelope.count("span"));
            assertEquals("two", text(envelope.get("span", 0)));
        }
    }

    @Test
    public void shouldCopyValueBytesOnSet() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);

            MutableDirectBufferEx value = new UnsafeBufferEx("one".getBytes(UTF_8));
            envelope.set("trace", value);
            value.putByte(0, (byte) 'X');

            assertEquals("one", text(envelope.get("trace", 0)));
        }
    }

    @Test
    public void shouldResetClearAccumulatedValues() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);
            envelope.set("trace", buffer("one"));

            envelope.reset();

            assertTrue(envelope.isEmpty());
            assertFalse(envelope.isOverflowed());
            assertEquals(0, envelope.count("trace"));
        }
    }

    @Test
    public void shouldWriteNothingWhenEmpty() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);

            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(256));
            Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder =
                new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                    .wrap(writeBuffer, 0, writeBuffer.capacity());

            envelope.writeHeaders(builder);
            Array32FW<KafkaHeaderFW> headers = builder.build();

            assertEquals(0, headers.fieldCount());
        }
    }

    @Test
    public void shouldWriteAccumulatedEntriesAsHeadersInOrder() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 64);

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
    }

    @Test
    public void shouldOverflowWhenReservationExceeded() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            // 4 (nameLen) + "trace".length=5 + 4 (valueLen) + "one".length=3 == 16 bytes exactly
            claim(envelope, logFile, 16);

            envelope.set("trace", buffer("one"));
            assertFalse(envelope.isOverflowed());

            envelope.set("span", buffer("two"));

            assertTrue(envelope.isOverflowed());
            assertEquals(1, envelope.count("trace"));
            assertEquals(0, envelope.count("span"));
        }
    }

    @Test
    public void shouldClearOverflowFlagOnReset() throws Exception
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();
        try (KafkaCacheFile logFile = newLogFile())
        {
            claim(envelope, logFile, 4);
            envelope.set("trace", buffer("one"));
            assertTrue(envelope.isOverflowed());

            envelope.reset();

            assertFalse(envelope.isOverflowed());
        }
    }

    @Test
    public void shouldDiscardSetsBeforeClaim()
    {
        KafkaCacheTrailerEnvelope envelope = new KafkaCacheTrailerEnvelope();

        envelope.set("trace", buffer("one"));

        assertTrue(envelope.isEmpty());
        assertFalse(envelope.isOverflowed());
    }

    private KafkaCacheFile newLogFile() throws Exception
    {
        Path location = tempFolder.newFile().toPath();
        MutableDirectBufferEx appendBuf = new UnsafeBufferEx(ByteBuffer.allocate(1024));
        return new KafkaCacheFile(location, 1024, appendBuf);
    }

    private static void claim(
        KafkaCacheTrailerEnvelope envelope,
        KafkaCacheFile logFile,
        int maxLength)
    {
        final int position = logFile.capacity();
        logFile.advance(position + maxLength);
        envelope.claim(logFile, position, maxLength);
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
