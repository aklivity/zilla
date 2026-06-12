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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufPipelineTest
{
    private final ProtobufSchema schema = newSchema();

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
        ProtobufTransform passthrough = (control, source, event, sink) -> sink.feed(control, source, event);
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

    private static Status feed(
        ProtobufPipeline pipeline,
        byte[] message)
    {
        return pipeline.feed(new UnsafeBuffer(message), 0, message.length);
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
        private byte[] segment;
        private int depth;

        private Capture(
            boolean segmentComposites)
        {
            this.segmentComposites = segmentComposites;
        }

        @Override
        public ProtobufPipeline.Status feed(
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
                events.add("V" + value(source));
                break;
            case SEGMENT:
                events.add("SEGMENT");
                segment = new byte[source.length()];
                source.buffer().getBytes(source.offset(), segment);
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
            segment = null;
            depth = 0;
        }

        private static String value(
            ProtobufSource source)
        {
            ProtobufField field = source.field();
            String value;
            switch (field.type())
            {
            case STRING:
                byte[] text = new byte[source.length()];
                source.buffer().getBytes(source.offset(), text);
                value = new String(text, UTF_8);
                break;
            case BYTES:
                value = "b" + source.length();
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
