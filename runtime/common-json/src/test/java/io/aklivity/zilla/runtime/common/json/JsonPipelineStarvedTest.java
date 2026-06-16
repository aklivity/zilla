/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonPipelineStarvedTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    void shouldStarveWhenWindowConsumedMidValue()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(gen));
        pipeline.reset();

        byte[] f1 = "{\"a\":1,".getBytes(UTF_8);
        Status status = pipeline.feed(new UnsafeBufferEx(f1), 0, f1.length, false);

        assertEquals(Status.STARVED, status);
    }

    @Test
    void shouldCompleteAcrossWindowsAfterStarved()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(gen));
        pipeline.reset();

        byte[] f1 = "{\"a\":1,".getBytes(UTF_8);
        byte[] f2 = "\"b\":2} ".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.feed(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.feed(new UnsafeBufferEx(f2), 0, f2.length, true);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"a\":1,\"b\":2}", new String(out, UTF_8));
    }

    @Test
    void shouldRejectTruncatedStructureWhenLast()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(gen));
        pipeline.reset();

        // the object never closes and this is the final window: truncated, not merely starved
        byte[] bytes = "{\"a\":1".getBytes(UTF_8);
        Status status = pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldRejectTruncatedStringWhenLast()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(gen));
        pipeline.reset();

        // an unterminated string at terminal EOF is malformed
        byte[] bytes = "{\"a\":\"abc".getBytes(UTF_8);
        Status status = pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldCompleteWholeValueWithDefaultFeed()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(gen));
        pipeline.reset();

        // the three-argument shorthand is last == true: a whole value completes in one shot
        byte[] bytes = "{\"a\":1}".getBytes(UTF_8);
        Status status = pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
    }

    @Test
    void shouldCompleteTrailingScalarAtTerminalEof()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(gen));
        pipeline.reset();

        // a bare trailing number with no delimiter completes because last == true makes EOF terminal
        byte[] bytes = "42".getBytes(UTF_8);
        Status status = pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("42", new String(out, UTF_8));
    }
}
