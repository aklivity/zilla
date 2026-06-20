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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static io.aklivity.zilla.runtime.engine.model.ModelPipeline.FLAGS_COMPLETE;
import static io.aklivity.zilla.runtime.engine.model.ModelPipeline.FLAGS_FIN;
import static io.aklivity.zilla.runtime.engine.model.ModelPipeline.FLAGS_INIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelHandler;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class HttpModelTest
{
    private final MutableDirectBuffer value = new UnsafeBuffer(new byte[256]);

    @Test
    public void shouldValidateWholeValue()
    {
        HttpModel model = HttpModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        assertTrue(model.validate(0L, 0L, value("hello"), 0, 5));
    }

    @Test
    public void shouldRejectInvalidValue()
    {
        HttpModel model = HttpModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        assertFalse(model.validate(0L, 0L, value("nope"), 0, 4));
    }

    @Test
    public void shouldValidateAcrossOverflow()
    {
        HttpModel model = HttpModel.decoder(handler(5), new UnsafeBuffer(new byte[2]));

        assertTrue(model.validate(0L, 0L, value("hello"), 0, 5));
    }

    @Test
    public void shouldValidateWhenNone()
    {
        assertSame(HttpModel.NONE, HttpModel.decoder(null, new UnsafeBuffer(new byte[8])));
        assertTrue(HttpModel.NONE.validate(0L, 0L, value("anything"), 0, 8));
    }

    @Test
    public void shouldTransformWholeContent()
    {
        HttpModel model = HttpModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        int consumed = model.transform(0L, 0L, FLAGS_COMPLETE, value("hello"), 0, 5, 256);

        assertEquals(5, consumed);
        assertEquals(5, model.produced());
        assertOutput(model, "hello");
    }

    @Test
    public void shouldRejectInvalidContent()
    {
        HttpModel model = HttpModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        int consumed = model.transform(0L, 0L, FLAGS_COMPLETE, value("nope"), 0, 4, 256);

        assertEquals(-1, consumed);
    }

    @Test
    public void shouldTransformContentAcrossFragments()
    {
        HttpModel model = HttpModel.decoder(handler(10), new UnsafeBuffer(new byte[256]));

        int consumed1 = model.transform(0L, 0L, FLAGS_INIT, value("hello"), 0, 5, 256);
        assertEquals(5, consumed1);
        assertEquals(5, model.produced());
        assertOutput(model, "hello");

        int consumed2 = model.transform(0L, 0L, FLAGS_FIN, value("world"), 0, 5, 256);
        assertEquals(5, consumed2);
        assertEquals(5, model.produced());
        assertOutput(model, "world");
    }

    @Test
    public void shouldTransformContentAcrossOverflow()
    {
        HttpModel model = HttpModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        int consumed1 = model.transform(0L, 0L, FLAGS_COMPLETE, value("hello"), 0, 5, 2);
        assertEquals(2, consumed1);
        assertEquals(2, model.produced());
        assertOutput(model, "he");

        int consumed2 = model.transform(0L, 0L, FLAGS_FIN, value("hello"), 2, 3, 256);
        assertEquals(3, consumed2);
        assertEquals(3, model.produced());
        assertOutput(model, "llo");
    }

    private static TestModelHandler handler(
        int length)
    {
        return new TestModelHandler(new TestModelConfig(length, emptyList(), true));
    }

    private MutableDirectBuffer value(
        String text)
    {
        value.putBytes(0, text.getBytes(UTF_8));
        return value;
    }

    private static void assertOutput(
        HttpModel model,
        String expected)
    {
        byte[] actual = new byte[model.produced()];
        model.buffer().getBytes(0, actual);
        assertEquals(expected, new String(actual, UTF_8));
    }
}
