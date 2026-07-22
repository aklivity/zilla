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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonPipelineDeferralTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    // a transform that needs each scalar whole: while a value still has deferred bytes it pushes back via
    // consumed(0) and does not forward; once the value is complete it forwards it downstream. The source
    // must accumulate the declined fragments and re-present the value whole rather than dropping them.
    private static final class WholeValueTransform implements JsonTransform
    {
        @Override
        public Status transform(
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
                status = sink.transform(control, source, event);
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
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // a bare string split across two windows: open-quote + 20 'a' (no close), then 20 'b' + close-quote
        byte[] f1 = "\"aaaaaaaaaaaaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbbbbbbbbbbbb\"".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, true);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("\"aaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb\"", new String(out, UTF_8));
    }

    @Test
    void shouldRejectWhenDeclinedValueExceedsCap()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        // cap the retained value at 16 chars; a decliner that grows the value past it must fail closed
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 16)))
            .transform(new WholeValueTransform())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // a bare string declined fragment by fragment grows past the 16-char cap on the second window
        byte[] f1 = "\"aaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbb".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, false);

        assertEquals(Status.REJECTED, status);
    }

    // Unconditionally declines every scalar it sees, regardless of source.deferredBytes() — simulating a
    // value that can never be reassembled (e.g. one that permanently exceeds some external bound the stage
    // enforces on its own terms). Used to prove the pump's own last-driven contract independent of any
    // particular decliner's reassembly logic.
    private static final class AlwaysDeclineTransform implements JsonTransform
    {
        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status;
            boolean scalar = event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER;
            if (scalar)
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }

    // Issue #2016: a stage that declines a scalar (consumed(0), STARVED) to force reassembly must still
    // resolve to REJECTED once last == true, rather than stall in STARVED forever. JsonPipeline documents
    // that STARVED is only returned when last == false (see JsonPipeline#transform javadoc), but the pump's
    // last-driven STARVED-to-REJECTED conversion only fired when its own driving loop ran out of events
    // (status == ADVANCED); a stage returning STARVED directly bypassed it entirely.
    @Test
    void shouldRejectPermanentlyDeclinedScalarOnTerminalWindow()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new AlwaysDeclineTransform())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // the scalar arrives whole and well-formed, but the stage declines it unconditionally; since this
        // is the final window, no further input is ever coming to satisfy the decliner
        byte[] bytes = "42".getBytes(UTF_8);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertEquals(Status.REJECTED, status);
    }
}
