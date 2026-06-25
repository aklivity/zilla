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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

class JsonPipelineChunkingTest
{
    private static final int BOUND = 32;

    @Test
    void shouldChunkLargeArrayThroughTerminalSink()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        String json = "[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19]";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkLargeObjectThroughTerminalSink()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        String json = "{\"k0\":0,\"k1\":1,\"k2\":2,\"k3\":3,\"k4\":4,\"k5\":5,\"k6\":6,\"k7\":7,\"k8\":8,\"k9\":9}";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkThroughProjectorTransform()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("")))
            .into(JsonEx.createSink(generator));

        String json = "{\"id\":1,\"items\":[10,11,12,13,14,15,16,17,18,19],\"ok\":true}";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkThroughValidatorTransform()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of("{\"type\":\"object\"}").validator())
            .into(JsonEx.createSink(generator));

        String json = "{\"k0\":0,\"k1\":1,\"k2\":2,\"k3\":3,\"k4\":4,\"k5\":5,\"k6\":6,\"k7\":7,\"k8\":8,\"k9\":9}";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkStructuredNumberValueThroughBoundedOutput()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        // a single decoded number lexeme longer than the output bound must suspend/resume mid-value,
        // propagating consumed bytes back to the parser rather than overrunning the generator's limit
        String json = "{\"n\":" + "1".repeat(100) + "}";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkStructuredControlHeavyStringThroughBoundedOutput()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        // a control-char-heavy string rendered canonically (short escapes) far exceeds the output bound:
        // the bounded write must suspend mid-string without splitting an escape, then resume byte-correct
        String json = "{\"data\":\"" + "\\n\\t\\r\\b\\f".repeat(12) + "\"}";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkStructuredMultibyteStringThroughBoundedOutput()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        // multibyte UTF-8 content far larger than the output bound: a suspend must fall on a code-point
        // boundary so no multibyte character is split across chunks
        String json = "{\"data\":\"" + "é中".repeat(20) + "\"}";
        assertEquals(json, chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldChunkStructuredStringAcrossInputAndOutputBounds()
    {
        // a decoded string larger than both the input window and the output bound, carrying escapes and a
        // multibyte char: with the input window wider than the output bound a single fragment overruns the
        // bound, so it starves on input and suspends mid-fragment on output, reconstructing canonically
        String content = ("\\né\\t" + "x".repeat(6)).repeat(8);
        String json = "{\"data\":\"" + content + "\"}";
        assertEquals(json, chunkedWindowed(JsonSink.Delivery.STRUCTURED, json, 40, 24));
    }

    @Test
    void shouldStreamStringValueAcrossInputFrames()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        // a single string value split mid-token across two input frames (larger than the input window)
        byte[] f1 = "{\"data\":\"aaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbb\"} ".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        assertEquals("{\"data\":\"aaaaaaaaaabbbbbbbbbb\"} ", new String(out, UTF_8));
    }

    @Test
    void shouldReportDeferredBytesWhileStreamingAcrossFrames()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        List<Boolean> deferred = new ArrayList<>();
        JsonTransform probe = (control, source, event, sink) ->
        {
            if (event == JsonEvent.SEGMENT)
            {
                deferred.add(source.deferredBytes());
            }
            return sink.transform(control, source, event);
        };
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(probe)
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        pipeline.transform(new UnsafeBufferEx("{\"data\":\"aaaaaaaaaa".getBytes(UTF_8)), 0, 19, false);
        pipeline.transform(new UnsafeBufferEx("bbbbbbbbbb\"} ".getBytes(UTF_8)), 0, 13);

        // first fragment is mid-stream (more deferred); the closing fragment completes the value
        assertTrue(deferred.get(0));
        assertFalse(deferred.get(deferred.size() - 1));
    }

    @Test
    void shouldFragmentLargeStringValueStructured()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        // a single string property value far larger than BOUND, in structured delivery
        String json = "{\"data\":\"" + "x".repeat(96) + "\"}";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldFragmentLargeNumberValueStructured()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        // a single numeric property value far larger than BOUND, in structured delivery
        String json = "{\"data\":" + "1".repeat(96) + "}";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldFragmentValueLargerThanBound()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        // one top-level array whose verbatim form far exceeds BOUND, delivered as a single segment that
        // must be fragmented across many chunks
        String json = "[\"aaaaaaaa\",\"bbbbbbbb\",\"cccccccc\",\"dddddddd\",\"eeeeeeee\",\"ffffffff\"]";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldFragmentValueThroughForwardingTransform()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        JsonTransform passthrough = (control, source, event, sink) -> sink.transform(control, source, event);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(passthrough)
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        // the resume cascade must continue the in-flight fragment through the transform stage
        String json = "[\"aaaaaaaa\",\"bbbbbbbb\",\"cccccccc\",\"dddddddd\",\"eeeeeeee\",\"ffffffff\"]";
        assertEquals(json + " ", chunked(pipeline, generator, output, json));
    }

    @Test
    void shouldCompleteWithoutSuspendWhenWithinBound()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        byte[] bytes = "[1,2,3] ".getBytes(UTF_8);
        pipeline.reset();
        generator.wrap(output, 0, 128);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        assertEquals("[1,2,3] ", new String(out, UTF_8));
    }

    @Test
    void shouldReconstructWindowFragmentedStringValueStructured()
    {
        // small feed windows fragment the over-window value; the structured sink re-renders each fragment
        // canonically from getStringView() with no concatenation
        String json = "{\"data\":\"" + "x".repeat(40) + "\"}";
        assertEquals(json, feedWindowed(json, JsonSink.Delivery.STRUCTURED, 8));
    }

    @Test
    void shouldReconstructFragmentedValueWithMultibyteAcrossWindow()
    {
        // a top-level string fills the first window and fragments; the window ends on the lead byte of
        // 'é', so the parser leaves the partial char unconsumed (position() < window) and the caller
        // carries the tail into the next window
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[512]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        String json = "\"" + "x".repeat(10) + "é" + "y".repeat(10) + "\"";
        byte[] msg = (json + " ").getBytes(UTF_8);
        int w1 = -1;
        for (int i = 0; i < msg.length && w1 < 0; i++)
        {
            if ((msg[i] & 0xff) == 0xc3)
            {
                w1 = i + 1; // window ends on the lead byte of 'é', splitting the char
            }
        }

        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(msg), 0, w1, false));
        assertEquals(1, pipeline.remaining(), "the split 'é' lead byte is the unconsumed partial unit");
        int progress = w1 - pipeline.remaining();
        assertEquals(Status.COMPLETED,
            pipeline.transform(new UnsafeBufferEx(msg), progress, msg.length, true));

        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        assertEquals(json, new String(out, UTF_8));
    }

    @Test
    void shouldReconstructWindowFragmentedNumberValueStructured()
    {
        String json = "{\"n\":" + "1".repeat(40) + "}";
        assertEquals(json, feedWindowed(json, JsonSink.Delivery.STRUCTURED, 8));
    }

    @Test
    void shouldReadWindowFragmentedNumberAsBigDecimalAndRejectGetInt()
    {
        // a number far longer than long range fragments across small windows. A consumer that needs the
        // whole value declines each fragment via consumed(0); the source accumulates the declined fragments
        // and re-presents the value whole, so at completion getBigDecimal() reads it from scratch while
        // getInt()/getLong() reject it as too large for a primitive
        String bigNumber = "12345678901234567890123456789012";
        String json = "{\"n\":" + bigNumber + "}";

        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        List<BigDecimal> wholes = new ArrayList<>();
        List<Boolean> intRejected = new ArrayList<>();
        JsonTransform probe = (control, source, event, sink) ->
        {
            Status result;
            if (event == JsonEvent.VALUE_NUMBER && source.deferredBytes())
            {
                // need the whole number; decline this fragment and wait for the rest
                control.consumed(0);
                result = Status.STARVED;
            }
            else
            {
                if (event == JsonEvent.VALUE_NUMBER)
                {
                    wholes.add(source.getBigDecimal());
                    boolean rejected = false;
                    try
                    {
                        source.getInt();
                    }
                    catch (IllegalStateException ex)
                    {
                        rejected = true;
                    }
                    intRejected.add(rejected);
                }
                result = sink.transform(control, source, event);
            }
            return result;
        };
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(probe)
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        byte[] msg = (json + " ").getBytes(UTF_8);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 100_000)
        {
            limit = Math.min(limit + 8, msg.length);
            boolean last = limit >= msg.length;
            status = pipeline.transform(new UnsafeBufferEx(msg), progress, limit, last);
            if (status != Status.STARVED)
            {
                break;
            }
            assertFalse(last, "last window must not starve");
            progress = limit - pipeline.remaining();
        }
        assertEquals(Status.COMPLETED, status);
        assertEquals(new BigDecimal(bigNumber), wholes.get(0));
        assertTrue(intRejected.get(0), "getInt() must reject a fragmented number");
    }

    // Feeds the document in fixed-size windows, carrying the unconsumed tail (remaining() bytes) across
    // feeds the way a real caller does; a value that fills a window is fragmented and reconstructed.
    private static String feedWindowed(
        String json,
        JsonSink.Delivery delivery,
        int window)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[512]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, delivery)));
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        byte[] msg = (json + " ").getBytes(UTF_8);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (guard++ < 100_000)
        {
            limit = Math.min(limit + window, msg.length);
            boolean last = limit >= msg.length;
            status = pipeline.transform(new UnsafeBufferEx(msg), progress, limit, last);
            if (status != Status.STARVED)
            {
                break;
            }
            assertFalse(last, "last window must not starve");
            progress = limit - pipeline.remaining();
        }
        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // Drives a value through both bounds at once: fixed-size input windows (carrying the unconsumed tail
    // across STARVED) and a bounded output (draining + re-targeting across SUSPENDED), concatenating every
    // drained chunk into one document. Asserts both axes engage at least once.
    private static String chunkedWindowed(
        JsonSink.Delivery delivery,
        String json,
        int inWindow,
        int outBound)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, delivery)));

        byte[] msg = (json + " ").getBytes(UTF_8);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        generator.wrap(output, 0, outBound);
        int progress = 0;
        int limit = 0;
        int suspends = 0;
        int starves = 0;
        int guard = 0;
        Status status = Status.STARVED;
        while (guard++ < 100_000)
        {
            if (status == Status.STARVED)
            {
                limit = Math.min(limit + inWindow, msg.length);
            }
            boolean last = limit >= msg.length;
            status = pipeline.transform(new UnsafeBufferEx(msg), progress, limit, last);
            byte[] chunk = new byte[generator.length()];
            output.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                suspends++;
            }
            else if (status == Status.STARVED)
            {
                assertFalse(last, "last window must not starve");
                starves++;
                progress = limit - pipeline.remaining();
            }
            else
            {
                break;
            }
            generator.wrap(output, 0, outBound);
        }
        assertEquals(Status.COMPLETED, status);
        assertTrue(suspends >= 1, "expected at least one SUSPENDED chunk boundary");
        assertTrue(starves >= 1, "expected at least one STARVED input boundary");
        return result.toString();
    }

    // Drives a value through the pipeline with the generator bounded at BOUND, draining and re-targeting
    // (context preserved) on each SUSPENDED, and concatenating the drained chunks into one document.
    private static String chunked(
        JsonPipeline pipeline,
        JsonGeneratorEx generator,
        MutableDirectBuffer output,
        String json)
    {
        byte[] bytes = (json + " ").getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        generator.wrap(output, 0, BOUND);
        int suspends = 0;
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.transform(in, 0, bytes.length);
            byte[] chunk = new byte[generator.length()];
            output.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                suspends++;
                generator.wrap(output, 0, BOUND);
            }
            guard++;
        }
        while (status == Status.SUSPENDED && guard < 10_000);
        assertEquals(Status.COMPLETED, status);
        assertTrue(suspends >= 1, "expected at least one SUSPENDED chunk boundary");
        return result.toString();
    }
}
