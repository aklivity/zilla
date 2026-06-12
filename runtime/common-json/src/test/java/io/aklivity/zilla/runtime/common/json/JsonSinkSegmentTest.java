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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonSinkSegmentTest
{
    private final JsonTransform segmentRoot = new JsonTransform()
    {
        private boolean armed;

        @Override
        public Status feed(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status = Status.ADVANCED;
            if (!armed && (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY))
            {
                armed = true;
                control.segmentable();
            }
            else
            {
                status = sink.feed(control, source, event);
            }
            return status;
        }
    };

    @Test
    void shouldWriteSegmentedObjectVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonSink.of(gen));

        byte[] bytes = "{ \"a\" : 1 } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{ \"a\" : 1 }", new String(out, UTF_8));
    }

    @Test
    void shouldSegmentWholeDocumentWithBareSegmentableSink()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonSink.of(gen, JsonSink.Delivery.SEGMENTABLE));

        byte[] bytes = "{ \"a\" : [1, 2], \"b\" : 3 } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{ \"a\" : [1, 2], \"b\" : 3 }", new String(out, UTF_8));
    }

    @Test
    void shouldWriteSegmentedArrayVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonSink.of(gen));

        byte[] bytes = "[ 1, 2 ] ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("[ 1, 2 ]", new String(out, UTF_8));
    }

    @Test
    void shouldWriteSegmentedNestedWhitespaceVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonSink.of(gen));

        byte[] bytes = "{ \"x\" : [1 ,2] } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{ \"x\" : [1 ,2] }", new String(out, UTF_8));
    }

    @Test
    void shouldCompleteAcrossFrames()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonSink.of(gen));

        byte[] first = "{\"a\":1,".getBytes(UTF_8);
        byte[] second = "\"b\":2} ".getBytes(UTF_8);

        pipeline.reset();
        assertEquals(Status.ADVANCED, pipeline.feed(new UnsafeBuffer(first), 0, first.length));
        Status status = pipeline.feed(new UnsafeBuffer(second), 0, second.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"a\":1,\"b\":2}", new String(out, UTF_8));
    }
}
