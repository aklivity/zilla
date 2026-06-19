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

class JsonPipelineDeferralTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);

    // a transform that needs each scalar whole: while a value still has deferred bytes it pushes back via
    // consumed(0) and does not forward; once the value is complete it forwards it downstream. The source
    // must accumulate the declined fragments and re-present the value whole rather than dropping them.
    private static final class WholeValueTransform implements JsonTransform
    {
        @Override
        public Status feed(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status;
            boolean scalar = event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER;
            if (scalar && source.deferredBytes())
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                status = sink.feed(control, source, event);
            }
            return status;
        }
    }

    @Test
    void shouldAccumulateDeferredStringAcrossWindows()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new WholeValueTransform())
            .into(JsonEx.createSink(gen));
        pipeline.reset();

        // a bare string split across two windows: open-quote + 20 'a' (no close), then 20 'b' + close-quote
        byte[] f1 = "\"aaaaaaaaaaaaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbbbbbbbbbbbb\"".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.feed(new UnsafeBuffer(f1), 0, f1.length, false));
        Status status = pipeline.feed(new UnsafeBuffer(f2), 0, f2.length, true);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("\"aaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb\"", new String(out, UTF_8));
    }
}
