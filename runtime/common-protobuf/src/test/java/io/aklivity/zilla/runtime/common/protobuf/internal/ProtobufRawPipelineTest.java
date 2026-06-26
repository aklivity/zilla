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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufRawPipelineTest
{
    @Test
    public void shouldCaptureSchemaFreeEvents()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.I32);
            w.writeFixed32(7);
        });

        Capture sink = new Capture();
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser()).into(sink);
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));
        assertEquals(List.of("{", "F1:VARINT", "V5", "F2:LEN", "L2", "F3:I32", "V7", "}"), sink.events);
    }

    @Test
    public void shouldCopyStructurally()
    {
        byte[] nested = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("x".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.I32);
            w.writeFixed32(7);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(nested);
        });

        assertArrayEquals(message, copy(message, null));
    }

    @Test
    public void shouldCopyGroupStructurally()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(5, ProtobufWireType.SGROUP);
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(9);
            w.writeTag(5, ProtobufWireType.EGROUP);
        });

        assertArrayEquals(message, copy(message, null));
    }

    @Test
    public void shouldRedactFieldByNumber()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("secret".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.I32);
            w.writeFixed32(7);
        });
        byte[] expected = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(3, ProtobufWireType.I32);
            w.writeFixed32(7);
        });

        ProtobufTransform redact = (control, source, event, sink) ->
            (event == ProtobufEvent.FIELD || event == ProtobufEvent.VALUE) && source.fieldNumber() == 2
                ? Status.ADVANCED
                : sink.transform(control, source, event);

        assertArrayEquals(expected, copy(message, redact));
    }

    private static byte[] copy(
        byte[] message,
        ProtobufTransform transform)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[4096]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufStream stream = Protobuf.stream(Protobuf.parser());
        if (transform != null)
        {
            stream = stream.transform(transform);
        }
        ProtobufPipeline pipeline = stream.into(ProtobufSink.of(generator));
        pipeline.reset();

        assertEquals(Status.COMPLETED, feed(pipeline, message));

        byte[] result = new byte[generator.length()];
        out.getBytes(0, result);
        return result;
    }

    private static Status feed(
        ProtobufPipeline pipeline,
        byte[] message)
    {
        return pipeline.transform(new UnsafeBufferEx(message), 0, message.length);
    }

    private static byte[] wire(
        Consumer<ProtobufWriter> body)
    {
        ExpandableArrayBufferEx buffer = new ExpandableArrayBufferEx();
        ProtobufWriter writer = new ProtobufWriter().wrap(buffer, 0);
        body.accept(writer);
        byte[] bytes = new byte[writer.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static final class Capture implements ProtobufSink
    {
        private final List<String> events = new ArrayList<>();
        private int depth;

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
            case FIELD:
                events.add("F" + source.fieldNumber() + ":" + source.wireType());
                break;
            case VALUE:
                events.add(source.wireType() == ProtobufWireType.LEN
                    ? "L" + source.segment().capacity()
                    : "V" + source.longValue());
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
            depth = 0;
        }

        @Override
        public boolean identity()
        {
            return false;
        }
    }
}
