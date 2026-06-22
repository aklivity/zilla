/*
 * Copyright 2021-2024 Aklivity Inc.
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
import static org.junit.Assert.assertTrue;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class KafkaExtractorTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[256]);

    @Test
    public void shouldCaptureSingleField()
    {
        KafkaExtractor extractor = new KafkaExtractor();

        onField(extractor, "$.id", "12345");

        assertEquals(5, extractor.extractedLength("$.id"));
        assertEquals("12345", read(extractor, "$.id"));
    }

    @Test
    public void shouldCaptureMultipleFields()
    {
        KafkaExtractor extractor = new KafkaExtractor();

        onField(extractor, "$.id", "abc");
        onField(extractor, "$.region", "east");

        assertEquals(3, extractor.extractedLength("$.id"));
        assertEquals(4, extractor.extractedLength("$.region"));
        assertEquals("abc", read(extractor, "$.id"));
        assertEquals("east", read(extractor, "$.region"));
    }

    @Test
    public void shouldReturnZeroForAbsentPath()
    {
        KafkaExtractor extractor = new KafkaExtractor();

        onField(extractor, "$.id", "abc");

        assertEquals(0, extractor.extractedLength("$.missing"));

        boolean[] visited = { false };
        extractor.extracted("$.missing", (p, b, i, l) -> visited[0] = true);
        assertFalse(visited[0]);
    }

    @Test
    public void shouldVisitPresentPath()
    {
        KafkaExtractor extractor = new KafkaExtractor();

        onField(extractor, "$.id", "abc");

        boolean[] visited = { false };
        extractor.extracted("$.id", (p, b, i, l) -> visited[0] = true);
        assertTrue(visited[0]);
    }

    @Test
    public void shouldClearOnReset()
    {
        KafkaExtractor extractor = new KafkaExtractor();

        onField(extractor, "$.id", "abc");
        extractor.reset();

        assertEquals(0, extractor.extractedLength("$.id"));
    }

    @Test
    public void shouldNotBleedAcrossValues()
    {
        KafkaExtractor extractor = new KafkaExtractor();

        onField(extractor, "$.id", "first");
        extractor.reset();
        onField(extractor, "$.region", "west");

        assertEquals(0, extractor.extractedLength("$.id"));
        assertEquals(4, extractor.extractedLength("$.region"));
        assertEquals("west", read(extractor, "$.region"));
    }

    private void onField(
        KafkaExtractor extractor,
        String path,
        String value)
    {
        byte[] bytes = value.getBytes(UTF_8);
        buffer.putBytes(0, bytes);
        extractor.onField(path, buffer, 0, bytes.length);
    }

    private String read(
        KafkaExtractor extractor,
        String path)
    {
        String[] result = { null };
        extractor.extracted(path, (p, b, i, l) -> result[0] = b.getStringWithoutLengthUtf8(i, l));
        return result[0];
    }
}
