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
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

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

public class MqttModelTest
{
    private final MutableDirectBuffer value = new UnsafeBuffer(new byte[256]);

    @Test
    public void shouldTransformWholeValue()
    {
        MqttModel model = MqttModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5);

        assertTrue(model.active());
        assertEquals(5, produced);
        assertValue(model, 5, "hello");
    }

    @Test
    public void shouldRejectInvalidValue()
    {
        MqttModel model = MqttModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        assertEquals(-1, model.transform(0L, 0L, value("nope"), 0, 4));
    }

    @Test
    public void shouldTransformWholeValueToLargerLength()
    {
        MqttModel model = MqttModel.decoder(handler(5, 8), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5);

        assertEquals(8, produced);
        assertValue(model, 5, "hello");
    }

    @Test
    public void shouldTransformWholeValueToSmallerLength()
    {
        MqttModel model = MqttModel.decoder(handler(5, 3), new UnsafeBuffer(new byte[256]));

        int produced = model.transform(0L, 0L, value("hello"), 0, 5);

        assertEquals(3, produced);
        assertValue(model, produced, "hel");
    }

    @Test
    public void shouldSupplyNoneWhenNoHandler()
    {
        MqttModel model = MqttModel.decoder(null, new UnsafeBuffer(new byte[8]));

        assertSame(MqttModel.NONE, model);
        assertFalse(model.active());
    }

    @Test
    public void shouldReportIdentityForValidator()
    {
        MqttModel model = MqttModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        // no transformed length configured means the value bytes pass through unchanged
        assertTrue(model.identity());
    }

    @Test
    public void shouldNotReportIdentityForTransformingModel()
    {
        MqttModel model = MqttModel.decoder(handler(5, 8), new UnsafeBuffer(new byte[256]));

        assertFalse(model.identity());
    }

    @Test
    public void shouldNotReportIdentityWhenNoHandler()
    {
        MqttModel model = MqttModel.decoder(null, new UnsafeBuffer(new byte[8]));

        assertFalse(model.identity());
    }

    @Test
    public void shouldValidateStreamedFragments()
    {
        MqttModel model = MqttModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        assertTrue(model.validate(0L, 0L, true, false, value("hello"), 0, 3));
        assertTrue(model.validate(0L, 0L, false, true, value("hello"), 3, 5));
    }

    @Test
    public void shouldRejectInvalidStreamedFragments()
    {
        MqttModel model = MqttModel.decoder(handler(5), new UnsafeBuffer(new byte[256]));

        // the whole value length does not match the configured length, so it is rejected on the final fragment
        assertFalse(model.validate(0L, 0L, true, true, value("nope"), 0, 4));
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

    private static void assertValue(
        MqttModel model,
        int length,
        String expected)
    {
        byte[] actual = new byte[length];
        model.buffer().getBytes(0, actual);
        assertEquals(expected, new String(actual, UTF_8));
    }
}
