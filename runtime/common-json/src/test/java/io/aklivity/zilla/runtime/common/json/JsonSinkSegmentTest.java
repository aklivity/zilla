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

import java.util.Map;

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
        public Status transform(
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
                status = sink.transform(control, source, event);
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
            .into(JsonEx.createSink(gen));

        byte[] bytes = "{ \"a\" : 1 } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{ \"a\" : 1 } ", new String(out, UTF_8));
    }

    @Test
    void shouldSegmentWholeDocumentWithBareSegmentableSink()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        byte[] bytes = "{ \"a\" : [1, 2], \"b\" : 3 } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{ \"a\" : [1, 2], \"b\" : 3 } ", new String(out, UTF_8));
    }

    @Test
    void shouldWriteSegmentedArrayVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonEx.createSink(gen));

        byte[] bytes = "[ 1, 2 ] ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("[ 1, 2 ] ", new String(out, UTF_8));
    }

    @Test
    void shouldWriteSegmentedNestedWhitespaceVerbatim()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonEx.createSink(gen));

        byte[] bytes = "{ \"x\" : [1 ,2] } ".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{ \"x\" : [1 ,2] } ", new String(out, UTF_8));
    }

    @Test
    void shouldCompleteAcrossFrames()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(segmentRoot)
            .into(JsonEx.createSink(gen));

        byte[] first = "{\"a\":1,".getBytes(UTF_8);
        byte[] second = "\"b\":2} ".getBytes(UTF_8);

        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBuffer(first), 0, first.length, false));
        Status status = pipeline.transform(new UnsafeBuffer(second), 0, second.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"a\":1,\"b\":2} ", new String(out, UTF_8));
    }

    @Test
    void shouldUseDefaultResumeAndResetForForwardingSink()
    {
        // a stage that only forwards events relies on the JsonSink default resume/reset:
        // reset is a no-op and resume reports nothing pending
        JsonSink sink = new JsonSink()
        {
            @Override
            public Status transform(
                JsonController control,
                JsonSource source,
                JsonEvent event)
            {
                return Status.ADVANCED;
            }

            @Override
            public boolean identity()
            {
                return false;
            }
        };
        sink.reset();
        assertEquals(Status.ADVANCED, sink.resume(null, null, null));
    }
}
