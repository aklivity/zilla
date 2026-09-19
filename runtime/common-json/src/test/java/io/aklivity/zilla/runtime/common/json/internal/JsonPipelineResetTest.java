/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

class JsonPipelineResetTest
{
    // Models a pooled generator returned to the pool mid-value: the first caller abandons a partial
    // array (leaving open structure in the generator), and a second caller checks the instance back out
    // and, per the pooling discipline, resets the pipeline before reuse. The reset must clear the stale
    // generator context so the next value is emitted clean (no leaked separator from the open array).
    @Test
    void shouldClearGeneratorContextOnResetForReuse()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        generator.wrap(buffer, 0, buffer.capacity());
        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx("[1,".getBytes(UTF_8)), 0, 3, false));

        pipeline.reset();
        generator.wrap(buffer, 0, buffer.capacity());
        byte[] bytes = "{\"b\":2} ".getBytes(UTF_8);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"b\":2} ", new String(out, UTF_8));
    }

    // Line-delimited JSON (and a multi-document YAML stream mapped through this pipeline) needs a document
    // boundary that isn't a hand-off to a wholly unrelated value: a stage's own cross-document accumulation
    // (e.g. a running count, a held key) must survive from one record to the next in the same session.
    // nextDocument() is that boundary; reset() remains reserved for pooled-instance reuse by an unrelated
    // caller, still cascading JsonTransform.reset() down the chain as before.
    @Test
    void shouldCarryTransformStateAcrossNextDocumentButNotAcrossReset()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        CountingTransform counting = new CountingTransform();
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(counting)
            .into(JsonEx.createSink(generator));

        pipeline.reset();
        generator.wrap(buffer, 0, buffer.capacity());
        byte[] first = "{\"a\":1}".getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(first), 0, first.length, true));
        byte[] firstOut = new byte[generator.length()];
        buffer.getBytes(0, firstOut);
        assertEquals("{\"a\":1}", new String(firstOut, UTF_8));
        assertEquals(1, counting.documentsStarted);

        pipeline.nextDocument();
        generator.wrap(buffer, 0, buffer.capacity());
        byte[] second = "{\"b\":2}".getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(second), 0, second.length, true));
        byte[] secondOut = new byte[generator.length()];
        buffer.getBytes(0, secondOut);
        assertEquals("{\"b\":2}", new String(secondOut, UTF_8));
        assertEquals(2, counting.documentsStarted);

        pipeline.reset();
        assertEquals(0, counting.documentsStarted);
    }

    @Test
    void shouldRejectNextDocumentBeforeCompletion()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createSink(JsonEx.createGenerator()));
        pipeline.reset();

        assertThrows(AssertionError.class, pipeline::nextDocument);
    }

    // A non-mediating pass-through stage: it forwards every event and the caller's own control unchanged,
    // so it never affects byte-preserving delivery, and only counts document starts to prove whether its
    // own state survived a document boundary.
    private static final class CountingTransform implements JsonTransform
    {
        private int documentsStarted;

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            if (event == JsonEvent.START_DOCUMENT)
            {
                documentsStarted++;
            }
            return sink.transform(control, source, event);
        }

        @Override
        public void reset()
        {
            documentsStarted = 0;
        }
    }
}
