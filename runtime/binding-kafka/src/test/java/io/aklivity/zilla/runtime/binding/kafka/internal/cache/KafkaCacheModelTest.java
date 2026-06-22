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
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelHandler;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class KafkaCacheModelTest
{
    private final MutableDirectBuffer value = new UnsafeBuffer(new byte[256]);
    private final MutableDirectBuffer output = new UnsafeBuffer(new byte[256]);
    private final MutableInteger outputLength = new MutableInteger();

    private final KafkaCacheModel.Output sink = (buffer, index, length) ->
    {
        output.putBytes(outputLength.value, buffer, index, length);
        outputLength.value += length;
    };

    @Test
    public void shouldTransformWholeValue()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(5, produced);
        assertOutput("hello");
    }

    @Test
    public void shouldRejectInvalidValue()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("nope"), 0, 4, sink);

        assertEquals(-1, produced);
    }

    @Test
    public void shouldTransformWholeValueToLargerLength()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5, 8), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(8, produced);
        assertEquals(8, outputLength.value);
        byte[] prefix = new byte[5];
        output.getBytes(0, prefix);
        assertArrayEquals("hello".getBytes(UTF_8), prefix);
    }

    @Test
    public void shouldTransformWholeValueToSmallerLength()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5, 3), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(3, produced);
        assertOutput("hel");
    }

    @Test
    public void shouldTransformAcrossOverflow()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), new UnsafeBuffer(new byte[2]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(5, produced);
        assertOutput("hello");
    }

    @Test
    public void shouldEncodeWholeValue()
    {
        KafkaCacheModel model = KafkaCacheModel.encoder(handler(3), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("abc"), 0, 3, sink);

        assertEquals(3, produced);
        assertOutput("abc");
    }

    @Test
    public void shouldForwardWhenNone()
    {
        int produced = KafkaCacheModel.NONE.transform(0L, 0L, value("passthrough"), 0, 11, sink);

        assertEquals(11, produced);
        assertOutput("passthrough");
    }

    @Test
    public void shouldReportNoneAsDefaults()
    {
        assertSame(KafkaCacheModel.NONE, KafkaCacheModel.decoder(null, new UnsafeBuffer(new byte[8])));
        assertSame(KafkaCacheModel.NONE, KafkaCacheModel.encoder(null, new UnsafeBuffer(new byte[8])));
        assertEquals(0, KafkaCacheModel.NONE.padding(value("x"), 0, 1));
        assertEquals(0, KafkaCacheModel.NONE.extractedLength("$.id"));

        boolean[] visited = { false };
        KafkaCacheModel.NONE.extracted("$.id", (p, b, i, l) -> visited[0] = true);
        assertEquals(false, visited[0]);

        KafkaCacheModel.NONE.reset();
    }

    @Test
    public void shouldReportZeroPadding()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        assertEquals(0, model.padding(value("hello"), 0, 5));
    }

    @Test
    public void shouldResetReusablePipeline()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        model.transform(0L, 0L, value("hello"), 0, 5, sink);
        model.reset();
        outputLength.value = 0;

        int produced = model.transform(0L, 0L, value("world"), 0, 5, sink);

        assertEquals(5, produced);
        assertOutput("world");
    }

    private static TestModelHandler handler(
        int length)
    {
        return new TestModelHandler(new TestModelConfig(length, emptyList(), true));
    }

    private static TestModelHandler handler(
        int length,
        int transformLength)
    {
        return new TestModelHandler(new TestModelConfig(length, emptyList(), true, transformLength));
    }

    private MutableDirectBuffer value(
        String text)
    {
        value.putBytes(0, text.getBytes(UTF_8));
        return value;
    }

    private void assertOutput(
        String expected)
    {
        byte[] actual = new byte[outputLength.value];
        output.getBytes(0, actual);
        assertArrayEquals(expected.getBytes(UTF_8), actual);
    }
}
