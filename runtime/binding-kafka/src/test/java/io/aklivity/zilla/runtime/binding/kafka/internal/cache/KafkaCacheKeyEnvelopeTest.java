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

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCacheKeyEnvelopeTest
{
    @Test
    public void shouldStartEmpty()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();

        assertTrue(envelope.isEmpty());
        assertEquals(0, envelope.count(":key"));
        assertNull(envelope.get(":key", 0));
    }

    @Test
    public void shouldRoundTripKeyOverride()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();

        envelope.set(":key", buffer("derived"));

        assertTrue(!envelope.isEmpty());
        assertEquals(1, envelope.count(":key"));
        assertEquals("derived", text(envelope.get(":key", 0)));
    }

    @Test
    public void shouldIgnoreOtherNames()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();

        envelope.set("other", buffer("ignored"));

        assertTrue(envelope.isEmpty());
        assertEquals(0, envelope.count("other"));
        assertNull(envelope.get("other", 0));
    }

    @Test
    public void shouldCopyValueBytesOnSet()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();
        MutableDirectBufferEx value = new UnsafeBufferEx("derived".getBytes(UTF_8));

        envelope.set(":key", value);
        value.putByte(0, (byte) 'X');

        assertEquals("derived", text(envelope.get(":key", 0)));
    }

    @Test
    public void shouldResetClearOverride()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();
        envelope.set(":key", buffer("derived"));

        envelope.reset();

        assertTrue(envelope.isEmpty());
        assertEquals(0, envelope.count(":key"));
        assertNull(envelope.get(":key", 0));
    }

    @Test
    public void shouldReuseBackingBufferAcrossMessages()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();
        envelope.set(":key", buffer("a-longer-derived-key"));
        envelope.reset();

        envelope.set(":key", buffer("short"));

        assertEquals("short", text(envelope.get(":key", 0)));
    }

    @Test
    public void shouldReportOutOfRangeIndexAsAbsent()
    {
        KafkaCacheKeyEnvelope envelope = new KafkaCacheKeyEnvelope();
        envelope.set(":key", buffer("derived"));

        assertNull(envelope.get(":key", 1));
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
