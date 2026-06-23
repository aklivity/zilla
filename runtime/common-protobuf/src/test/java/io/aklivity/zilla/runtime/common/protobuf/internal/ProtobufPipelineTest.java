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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufPipelineTest
{
    private final ProtobufSchema schema = newSchema();
    // captures the call-scoped diagnostic the pipeline pushes on a terminal REJECTED, copying the message out
    private final String[] reason = new String[1];
    private final ProtobufReporter reporter = d -> reason[0] = d.message();

    private boolean sawSuspended;
    private boolean sawStarved;

    @Test
    public void shouldValidateProto3Complete()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
        });

        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"))
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
    }

    @Test
    public void shouldCaptureStructuredEvents()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(7);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{", "F1", "Vneo", "F2", "V7", "F4", "{", "F1", "VZion", "}", "}"), sink.events);
    }

    @Test
    public void shouldDeliverNestedMessageAsSegment()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        Capture sink = new Capture(true);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{", "F4", "SEGMENT", "}"), sink.events);
        assertArrayEquals(home, sink.segment);
    }

    @Test
    public void shouldEmitFieldAndValuePerPackedElement()
    {
        byte[] block = wire(w ->
        {
            w.writeVarint64(1);
            w.writeVarint64(2);
            w.writeVarint64(3);
        });
        byte[] message = wire(w ->
        {
            w.writeTag(3, ProtobufWireType.LEN);
            w.writeBytes(block);
        });

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{", "F3", "V1", "F3", "V2", "F3", "V3", "}"), sink.events);
    }

    @Test
    public void shouldDecodeProto2Group()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(5, ProtobufWireType.SGROUP);
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(9);
            w.writeTag(5, ProtobufWireType.EGROUP);
        });

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{", "F5", "(", "F1", "V9", ")", "}"), sink.events);
    }

    @Test
    public void shouldSkipUnknownField()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(99, ProtobufWireType.VARINT);
            w.writeVarint64(123);
        });

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{", "F2", "V1", "}"), sink.events);
    }

    @Test
    public void shouldRejectMalformed()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"))
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        assertEquals(Status.REJECTED, feed(pipeline, new byte[]{(byte) 0x10, (byte) 0x80}));
    }

    @Test
    public void shouldRejectUnknownRootMessage()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "Nope")).into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        assertEquals(Status.REJECTED, feed(pipeline, new byte[0]));
    }

    @Test
    public void shouldCompleteWhenRequiredPresent()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("x".getBytes(UTF_8));
        });

        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "R"))
            .transform(schema.validator("R"))
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
    }

    @Test
    public void shouldRejectWhenRequiredMissing()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(4);
        });

        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "R"))
            .transform(schema.validator("R"))
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        assertEquals(Status.REJECTED, feed(pipeline, message));
    }

    @Test
    public void shouldReportNamedFieldWhenRequiredMissing()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(4);
        });

        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "R"))
            .transform(schema.validator("R"))
            .reporting(reporter)
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        // the structural reject names the missing required field "a", closing the reason-less-reject gap
        assertEquals(Status.REJECTED, feed(pipeline, message));
        assertTrue(reason[0].contains("a"), reason[0]);
        assertTrue(reason[0].contains("required"), reason[0]);
    }

    @Test
    public void shouldReportReasonWhenMalformed()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .reporting(reporter)
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        assertEquals(Status.REJECTED, feed(pipeline, new byte[]{(byte) 0x10, (byte) 0x80}));
        assertNotNull(reason[0]);
    }

    @Test
    public void shouldNotReportOnStarved()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(300);
        });

        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .reporting(reporter)
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        // back-pressure is not failure: a value split mid-varint STARVES without firing the reporter
        UnsafeBuffer buffer = new UnsafeBuffer(message);
        assertEquals(Status.STARVED, pipeline.transform(buffer, 0, message.length - 1, false));
        assertNull(reason[0]);
    }

    @Test
    public void shouldReuseAcrossMessages()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });

        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"))
            .into(new ProtobufDiscardSinkImpl());

        pipeline.reset();
        assertEquals(Status.COMPLETED, feed(pipeline, message));
        pipeline.reset();
        assertEquals(Status.COMPLETED, feed(pipeline, message));
    }

    @Test
    public void shouldDecodeAllScalarTypes()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(-5);
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeZigzag32(-6);
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(7);
            w.writeTag(4, ProtobufWireType.I32);
            w.writeFixed32(8);
            w.writeTag(5, ProtobufWireType.I32);
            w.writeFixed32(-8);
            w.writeTag(6, ProtobufWireType.VARINT);
            w.writeVarint64(9);
            w.writeTag(7, ProtobufWireType.VARINT);
            w.writeVarint64(10);
            w.writeTag(8, ProtobufWireType.VARINT);
            w.writeZigzag64(-11);
            w.writeTag(9, ProtobufWireType.I64);
            w.writeFixed64(12);
            w.writeTag(10, ProtobufWireType.I64);
            w.writeFixed64(-13);
            w.writeTag(11, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(12, ProtobufWireType.I64);
            w.writeFixed64(Double.doubleToLongBits(1.5));
            w.writeTag(13, ProtobufWireType.I32);
            w.writeFixed32(Float.floatToIntBits(0.25f));
            w.writeTag(14, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(15, ProtobufWireType.LEN);
            w.writeBytes(new byte[]{1, 2, 3});
        });

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "S")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{",
            "F1", "V-5", "F2", "V-6", "F3", "V7", "F4", "V8", "F5", "V-8",
            "F6", "V9", "F7", "V10", "F8", "V-11", "F9", "V12", "F10", "V-13",
            "F11", "V1", "F12", "V1.5", "F13", "V0.25", "F14", "Vhi", "F15", "Vb3",
            "}"), sink.events);
    }

    @Test
    public void shouldForwardThroughDefaultResetTransform()
    {
        ProtobufTransform passthrough = (control, source, event, sink) -> sink.transform(control, source, event);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(passthrough)
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();

        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });
        assertEquals(Status.COMPLETED, feed(pipeline, message));
    }

    @Test
    public void shouldRejectScalarWireTypeMismatch()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("oops".getBytes(UTF_8));
        });
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(new ProtobufDiscardSinkImpl());
        pipeline.reset();
        assertEquals(Status.REJECTED, feed(pipeline, message));
    }

    @Test
    public void shouldRejectMessageWireTypeMismatch()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(new ProtobufDiscardSinkImpl());
        pipeline.reset();
        assertEquals(Status.REJECTED, feed(pipeline, message));
    }

    @Test
    public void shouldRejectGroupWireTypeMismatch()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes("oops".getBytes(UTF_8));
        });
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(new ProtobufDiscardSinkImpl());
        pipeline.reset();
        assertEquals(Status.REJECTED, feed(pipeline, message));
    }

    @Test
    public void shouldValidateValidMessage()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
        });
        assertTrue(schema.validate("P", new UnsafeBuffer(message), 0, message.length));
    }

    @Test
    public void shouldValidateRejectMalformedMessage()
    {
        assertFalse(schema.validate("P", new UnsafeBuffer(new byte[]{(byte) 0x10, (byte) 0x80}), 0, 2));
    }

    @Test
    public void shouldValidateRejectUnknownMessage()
    {
        assertFalse(schema.validate("Nope", new UnsafeBuffer(new byte[0]), 0, 0));
    }

    @Test
    public void shouldValidateAcceptRequiredPresent()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("x".getBytes(UTF_8));
        });
        assertTrue(schema.validate("R", new UnsafeBuffer(message), 0, message.length));
    }

    @Test
    public void shouldValidateRejectRequiredMissing()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(4);
        });
        assertFalse(schema.validate("R", new UnsafeBuffer(message), 0, message.length));
    }

    @Test
    public void shouldStreamAcrossTinyWindows()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(7);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        List<String> expected = capture(message);

        for (int window : new int[]{1, 2, 3, 7})
        {
            Capture sink = new Capture(false);
            ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
            pipeline.reset();

            Status last = feedWindows(pipeline, message, window);

            assertEquals(Status.COMPLETED, last, "window=" + window);
            assertEquals(expected, sink.events, "window=" + window);
        }
    }

    @Test
    public void shouldResumeMidVarintSplit()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(300);
        });

        UnsafeBuffer buffer = new UnsafeBuffer(message);
        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        // the value 300 is a two-byte varint; split the window between its bytes
        assertEquals(Status.STARVED, pipeline.transform(buffer, 0, message.length - 1, false));
        int progress = (message.length - 1) - pipeline.remaining();
        assertEquals(Status.COMPLETED, pipeline.transform(buffer, progress, message.length, true));
        assertEquals(List.of("{", "F2", "V300", "}"), sink.events);
    }

    @Test
    public void shouldStreamNestedMessageAcrossWindows()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        List<String> expected = capture(message);

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        // split inside the nested message body so remaining must survive the window swap
        UnsafeBuffer buffer = new UnsafeBuffer(message);
        int split = message.length - 2;
        assertEquals(Status.STARVED, pipeline.transform(buffer, 0, split, false));
        int progress = split - pipeline.remaining();
        assertEquals(Status.COMPLETED, pipeline.transform(buffer, progress, message.length, true));
        assertEquals(expected, sink.events);
    }

    @Test
    public void shouldRejectTruncatedMidTagWhenLast()
    {
        // a varint tag that never terminates, with last = true
        assertEquals(Status.REJECTED, feedLast(new byte[]{(byte) 0x80}));
    }

    @Test
    public void shouldRejectTruncatedMidVarintWhenLast()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(300);
        });
        // drop the final continuation byte of the value, end of input declared
        assertEquals(Status.REJECTED, feedLast(prefix(message, message.length - 1)));
    }

    @Test
    public void shouldRejectTruncatedMidLengthWhenLast()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
        });
        // keep the tag and length prefix but drop the value bytes
        assertEquals(Status.REJECTED, feedLast(prefix(message, 2)));
    }

    @Test
    public void shouldRejectTruncatedNestedWhenLast()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });
        // nested message declared longer than the bytes that arrive
        assertEquals(Status.REJECTED, feedLast(prefix(message, message.length - 2)));
    }

    @Test
    public void shouldCompleteOnCleanBoundaryWhenLast()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(7);
        });

        // first window ends exactly after field 1; the final window carries field 2 with last = true
        UnsafeBuffer buffer = new UnsafeBuffer(message);
        int split = 5; // tag(1) + len(1) + "neo"(3)
        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.STARVED, pipeline.transform(buffer, 0, split, false));
        int progress = split - pipeline.remaining();
        assertEquals(Status.COMPLETED, pipeline.transform(buffer, progress, message.length, true));
        assertEquals(List.of("{", "F1", "Vneo", "F2", "V7", "}"), sink.events);
    }

    @Test
    public void shouldInterleaveSuspendedAndStarved()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("a-rather-long-city-name".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(7);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        // one-shot reference: whole input, ample output — neither axis triggers
        byte[] whole = transcode(message, message.length, 1 << 16);
        assertFalse(sawSuspended);
        assertFalse(sawStarved);

        // tiny input windows, ample output: input streaming alone must preserve the output bytes exactly
        byte[] starvedOnly = transcode(message, 3, 1 << 16);
        assertTrue(sawStarved);
        assertFalse(sawSuspended);
        assertArrayEquals(whole, starvedOnly);

        // tiny input windows and a tiny output limit: both axes compose to a clean completion
        byte[] chunked = transcode(message, 3, 16);
        assertTrue(sawStarved);
        assertTrue(sawSuspended);
        assertTrue(chunked.length > 0);
    }

    @Test
    public void shouldStreamLargeStringEndToEnd()
    {
        String text = "the quick brown fox jumps over the lazy dog, then does it all again twice";
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(text.getBytes(UTF_8));
        });

        byte[] whole = transcode(message, message.length, 1 << 16);
        byte[] chunked = transcode(message, 5, 24);
        assertTrue(sawStarved);
        assertTrue(sawSuspended);

        assertEquals(text, decodeName(whole));
        assertEquals(text, decodeName(chunked));
    }

    @Test
    public void shouldStreamGroupAcrossWindows()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(5, ProtobufWireType.SGROUP);
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(9);
            w.writeTag(5, ProtobufWireType.EGROUP);
        });

        List<String> expected = capture(message);

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feedWindows(pipeline, message, 1));
        assertEquals(expected, sink.events);
    }

    @Test
    public void shouldStreamUnknownLengthFieldAcrossWindows()
    {
        byte[] big = new byte[40];
        for (int i = 0; i < big.length; i++)
        {
            big[i] = (byte) (i + 1);
        }
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(7);
            w.writeTag(99, ProtobufWireType.LEN);
            w.writeBytes(big);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
        });

        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();

        int window = 6;
        int progress = 0;
        int limit = 0;
        int maxRetained = 0;
        Status status = Status.STARVED;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(window, message.length - limit);
            limit += take;
            boolean last = limit >= message.length;
            status = pipeline.transform(new UnsafeBuffer(message), progress, limit, last);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
                maxRetained = Math.max(maxRetained, limit - progress);
            }
            else
            {
                done = true;
            }
        }

        assertEquals(Status.COMPLETED, status);
        assertEquals(List.of("{", "F2", "V7", "F1", "Vneo", "}"), sink.events);
        // the unknown field is discarded a window at a time, so the retained tail never grows to its size
        assertTrue(maxRetained < big.length, "retained tail grew to " + maxRetained);
    }

    @Test
    public void shouldStreamSchemaFreeValueAcrossWindows()
    {
        byte[] data = new byte[40];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte) (i * 7 + 1);
        }
        byte[] message = wire(w ->
        {
            w.writeTag(7, ProtobufWireType.LEN);
            w.writeBytes(data);
        });

        byte[] whole = transcodeUntyped(message, message.length);
        byte[] chunked = transcodeUntyped(message, 6);
        assertArrayEquals(whole, chunked);
        assertArrayEquals(message, chunked);
    }

    private String decodeName(
        byte[] wire)
    {
        ProtobufParser parser = Protobuf.parser(schema, "P").wrap(new UnsafeBuffer(wire), 0, wire.length);
        String name = null;
        ProtobufField field = null;
        while (parser.hasNext())
        {
            ProtobufEvent event = parser.nextEvent();
            if (event == ProtobufEvent.FIELD)
            {
                field = parser.field();
            }
            else if (event == ProtobufEvent.VALUE && field != null && field.number() == 1)
            {
                byte[] bytes = new byte[parser.segment().capacity()];
                parser.segment().getBytes(0, bytes);
                name = new String(bytes, UTF_8);
            }
        }
        return name;
    }

    private byte[] transcodeUntyped(
        byte[] message,
        int window)
    {
        UnsafeBuffer output = new UnsafeBuffer(new byte[4096]);
        ProtobufGenerator generator = Protobuf.generator().wrap(output, 0, output.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser())
            .into(ProtobufSink.of(generator));
        pipeline.reset();

        UnsafeBuffer in = new UnsafeBuffer(message);
        int progress = 0;
        int limit = 0;
        boolean completed = false;
        while (!completed)
        {
            int take = Math.min(window, message.length - limit);
            limit += take;
            boolean last = limit >= message.length;
            Status status = pipeline.transform(in, progress, limit, last);
            switch (status)
            {
            case STARVED:
                progress = limit - pipeline.remaining();
                break;
            case COMPLETED:
                completed = true;
                break;
            default:
                throw new AssertionError("unexpected status " + status);
            }
        }

        byte[] bytes = new byte[generator.length()];
        output.getBytes(0, bytes);
        return bytes;
    }

    private byte[] transcode(
        byte[] message,
        int window,
        int cap)
    {
        sawSuspended = false;
        sawStarved = false;

        UnsafeBuffer output = new UnsafeBuffer(new byte[cap]);

        ProtobufGenerator generator = Protobuf.generator().wrap(output, 0, cap);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .into(ProtobufSink.of(generator, schema, "P"));
        pipeline.reset();

        ExpandableArrayBuffer drained = new ExpandableArrayBuffer();
        int drainedLength = 0;

        UnsafeBuffer in = new UnsafeBuffer(message);
        int progress = 0;
        int limit = 0;
        boolean completed = false;
        int guard = 0;
        while (!completed)
        {
            if (guard++ > 100000)
            {
                throw new AssertionError("pipeline failed to converge");
            }
            int take = Math.min(window, message.length - limit);
            limit += take;
            boolean last = limit >= message.length;
            // the retained tail [progress, oldLimit) plus the new window is just message[progress, limit)
            Status status = pipeline.transform(in, progress, limit, last);
            while (status == Status.SUSPENDED)
            {
                sawSuspended = true;
                drained.putBytes(drainedLength, output, 0, generator.length());
                drainedLength += generator.length();
                generator.wrap(output, 0, cap);
                status = pipeline.transform(in, progress, limit, last);
            }
            switch (status)
            {
            case STARVED:
                sawStarved = true;
                progress = limit - pipeline.remaining();
                break;
            case COMPLETED:
                drained.putBytes(drainedLength, output, 0, generator.length());
                drainedLength += generator.length();
                completed = true;
                break;
            default:
                throw new AssertionError("unexpected status " + status);
            }
        }

        byte[] bytes = new byte[drainedLength];
        drained.getBytes(0, bytes);
        return bytes;
    }

    private List<String> capture(
        byte[] message)
    {
        Capture sink = new Capture(false);
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(sink);
        pipeline.reset();
        assertEquals(Status.COMPLETED, feed(pipeline, message));
        return List.copyOf(sink.events);
    }

    private Status feedLast(
        byte[] message)
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P")).into(new ProtobufDiscardSinkImpl());
        pipeline.reset();
        return pipeline.transform(new UnsafeBuffer(message), 0, message.length, true);
    }

    // feeds a message window-by-window, retaining the unconsumed tail (remaining() bytes) across feeds the
    // way a real caller does; for pipelines with no bounded output (no SUSPENDED)
    private static Status feedWindows(
        ProtobufPipeline pipeline,
        byte[] message,
        int window)
    {
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(window, message.length - limit);
            // the retained tail is message[progress, limit); appending the next window keeps it contiguous
            limit += take;
            boolean last = limit >= message.length;
            status = pipeline.transform(new UnsafeBuffer(message), progress, limit, last);
            if (status == Status.STARVED)
            {
                assertFalse(last, "last window must not starve");
                progress = limit - pipeline.remaining();
            }
            else
            {
                done = true;
            }
        }
        return status;
    }

    private static byte[] prefix(
        byte[] message,
        int length)
    {
        byte[] bytes = new byte[length];
        System.arraycopy(message, 0, bytes, 0, length);
        return bytes;
    }

    private static Status feed(
        ProtobufPipeline pipeline,
        byte[] message)
    {
        return pipeline.transform(new UnsafeBuffer(message), 0, message.length);
    }

    private static byte[] wire(
        Consumer<ProtobufWriter> body)
    {
        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
        ProtobufWriter writer = new ProtobufWriter().wrap(buffer, 0);
        body.accept(writer);
        byte[] bytes = new byte[writer.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("Addr")
                .field(ProtobufField.builder().number(1).name("city").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Grp")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("P")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(3).name("nums").type(ProtobufType.INT32).repeated(true).build())
                .field(ProtobufField.builder().number(4).name("home").type(ProtobufType.MESSAGE).typeName("Addr").build())
                .field(ProtobufField.builder().number(5).name("grp").type(ProtobufType.GROUP).typeName("Grp").build())
                .build())
            .message(ProtobufMessage.builder("R")
                .field(ProtobufField.builder().number(1).name("a").type(ProtobufType.STRING).required(true).build())
                .field(ProtobufField.builder().number(2).name("b").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("S")
                .field(ProtobufField.builder().number(1).name("i32").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(2).name("si32").type(ProtobufType.SINT32).build())
                .field(ProtobufField.builder().number(3).name("u32").type(ProtobufType.UINT32).build())
                .field(ProtobufField.builder().number(4).name("f32").type(ProtobufType.FIXED32).build())
                .field(ProtobufField.builder().number(5).name("sf32").type(ProtobufType.SFIXED32).build())
                .field(ProtobufField.builder().number(6).name("i64").type(ProtobufType.INT64).build())
                .field(ProtobufField.builder().number(7).name("u64").type(ProtobufType.UINT64).build())
                .field(ProtobufField.builder().number(8).name("si64").type(ProtobufType.SINT64).build())
                .field(ProtobufField.builder().number(9).name("f64").type(ProtobufType.FIXED64).build())
                .field(ProtobufField.builder().number(10).name("sf64").type(ProtobufType.SFIXED64).build())
                .field(ProtobufField.builder().number(11).name("b").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(12).name("d").type(ProtobufType.DOUBLE).build())
                .field(ProtobufField.builder().number(13).name("fl").type(ProtobufType.FLOAT).build())
                .field(ProtobufField.builder().number(14).name("s").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(15).name("by").type(ProtobufType.BYTES).build())
                .build())
            .build();
    }

    private static final class Capture implements ProtobufSink
    {
        private final boolean segmentComposites;
        private final List<String> events = new ArrayList<>();
        private final ByteArrayOutputStream valueBytes = new ByteArrayOutputStream();
        private byte[] segment;
        private int depth;

        private Capture(
            boolean segmentComposites)
        {
            this.segmentComposites = segmentComposites;
        }

        @Override
        public ProtobufPipeline.Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
            switch (event)
            {
            case START_MESSAGE:
                events.add("{");
                depth++;
                break;
            case END_MESSAGE:
                events.add("}");
                depth--;
                if (depth == 0)
                {
                    status = ProtobufPipeline.Status.COMPLETED;
                }
                break;
            case START_GROUP:
                events.add("(");
                depth++;
                break;
            case END_GROUP:
                events.add(")");
                depth--;
                break;
            case FIELD:
                ProtobufField field = source.field();
                events.add("F" + field.number());
                if (segmentComposites && field.composite())
                {
                    control.segmentable();
                }
                break;
            case VALUE:
                ProtobufField valueField = source.field();
                if (valueField != null &&
                    (valueField.type() == ProtobufType.STRING || valueField.type() == ProtobufType.BYTES))
                {
                    // a string/bytes value may arrive in chunks; reassemble before recording
                    byte[] chunk = new byte[source.segment().capacity()];
                    source.segment().getBytes(0, chunk);
                    valueBytes.writeBytes(chunk);
                    if (source.deferredBytes() == 0)
                    {
                        events.add(valueField.type() == ProtobufType.STRING
                            ? "V" + valueBytes.toString(UTF_8)
                            : "Vb" + valueBytes.size());
                        valueBytes.reset();
                    }
                }
                else
                {
                    events.add("V" + value(source));
                }
                break;
            case SEGMENT:
                events.add("SEGMENT");
                segment = new byte[source.segment().capacity()];
                source.segment().getBytes(0, segment);
                break;
            default:
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            events.clear();
            valueBytes.reset();
            segment = null;
            depth = 0;
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        private static String value(
            ProtobufSource source)
        {
            ProtobufField field = source.field();
            String value;
            switch (field.type())
            {
            case STRING:
                byte[] text = new byte[source.segment().capacity()];
                source.segment().getBytes(0, text);
                value = new String(text, UTF_8);
                break;
            case BYTES:
                value = "b" + source.segment().capacity();
                break;
            case DOUBLE:
                value = Double.toString(source.doubleValue());
                break;
            case FLOAT:
                value = Float.toString(source.floatValue());
                break;
            default:
                value = Long.toString(source.longValue());
                break;
            }
            return value;
        }
    }
}
