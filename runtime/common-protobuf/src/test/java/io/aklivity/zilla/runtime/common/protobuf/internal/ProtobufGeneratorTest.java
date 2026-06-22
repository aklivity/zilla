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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

public class ProtobufGeneratorTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldGenerateWireDecodableAgainstSchema()
    {
        MutableDirectBufferEx nested = new UnsafeBufferEx(new byte[64]);
        ProtobufGenerator inner = Protobuf.generator().wrap(nested, 0, nested.capacity());
        inner.writeInt32(1, 9);
        int nestedLength = inner.length();

        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[256]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        generator
            .writeInt32(1, -5)
            .writeSInt32(2, -6)
            .writeUInt32(3, (int) 4000000000L)
            .writeFixed32(4, 7)
            .writeDouble(5, 1.5)
            .writeBool(6, true)
            .writeString(7, "hi")
            .writeBytes(8, new byte[]{1, 2, 3})
            .writeEnum(9, 1)
            .writeMessage(10, nested, 0, nestedLength);

        Capture sink = new Capture();
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "All")).into(sink);
        pipeline.reset();
        ProtobufPipeline.Status status = pipeline.transform(out, 0, generator.length());

        assertEquals(ProtobufPipeline.Status.COMPLETED, status);
        assertEquals(List.of("{",
            "F1", "V-5", "F2", "V-6", "F3", "V4000000000", "F4", "V7", "F5", "V1.5",
            "F6", "V1", "F7", "Vhi", "F8", "Vb3", "F9", "V1", "F10", "{", "F1", "V9", "}",
            "}"), sink.events);
    }

    @Test
    public void shouldGenerateGroupDecodableAgainstSchema()
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        generator
            .writeInt32(1, -5)
            .startGroup(11).writeInt32(1, 9).endGroup();

        Capture sink = new Capture();
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "All")).into(sink);
        pipeline.reset();
        ProtobufPipeline.Status status = pipeline.transform(out, 0, generator.length());

        assertEquals(ProtobufPipeline.Status.COMPLETED, status);
        assertEquals(List.of("{", "F1", "V-5", "F11", "(", "F1", "V9", ")", "}"), sink.events);
    }

    @Test
    public void shouldStreamNestedMessageMatchingPreEncoded()
    {
        MutableDirectBufferEx nested = new UnsafeBufferEx(new byte[64]);
        int nestedLength = Protobuf.generator().wrap(nested, 0, nested.capacity()).writeInt32(1, 9).length();

        MutableDirectBufferEx preEncodedOut = new UnsafeBufferEx(new byte[128]);
        ProtobufGenerator preEncoded = Protobuf.generator().wrap(preEncodedOut, 0, preEncodedOut.capacity());
        preEncoded.writeInt32(1, 7).writeMessage(2, nested, 0, nestedLength);
        byte[] expected = bytes(preEncodedOut, preEncoded.length());

        MutableDirectBufferEx streamedOut = new UnsafeBufferEx(new byte[128]);
        ProtobufGenerator streamed = Protobuf.generator().wrap(streamedOut, 0, streamedOut.capacity());
        streamed.writeInt32(1, 7).startMessage(2, nestedLength).writeInt32(1, 9).endMessage();
        byte[] actual = bytes(streamedOut, streamed.length());

        assertArrayEquals(expected, actual);
    }

    @Test
    public void shouldReproduceWireFromParserEvents()
    {
        int nestedLength = Protobuf.generator().wrap(new UnsafeBufferEx(new byte[16]), 0, 16).writeInt32(1, 9).length();
        MutableDirectBufferEx in = new UnsafeBufferEx(new byte[256]);
        ProtobufGenerator source = Protobuf.generator().wrap(in, 0, in.capacity());
        source
            .writeInt32(1, -5)
            .writeSInt32(2, -6)
            .writeUInt32(3, (int) 4000000000L)
            .writeFixed32(4, 7)
            .writeDouble(5, 1.5)
            .writeBool(6, true)
            .writeString(7, "hi")
            .writeBytes(8, new byte[]{1, 2, 3})
            .writeEnum(9, 1)
            .startMessage(10, nestedLength).writeInt32(1, 9).endMessage();
        byte[] input = bytes(in, source.length());

        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[256]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "All")).into(new Mirror(generator));
        pipeline.reset();

        assertEquals(ProtobufPipeline.Status.COMPLETED, pipeline.transform(in, 0, input.length));
        assertArrayEquals(input, bytes(out, generator.length()));
    }

    @Test
    public void shouldRejectLimitExceedingCapacity()
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> Protobuf.generator().wrap(out, 0, 17));
    }

    @Test
    public void shouldPadNestedLengthWhenBodySmallerThanOptimistic()
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[256]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        generator.startMessage(10, 200).writeInt32(1, 9).endMessage();

        Capture sink = new Capture();
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "All")).into(sink);
        pipeline.reset();

        assertEquals(ProtobufPipeline.Status.COMPLETED, pipeline.transform(out, 0, generator.length()));
        assertEquals(List.of("{", "F10", "{", "F1", "V9", "}", "}"), sink.events);
    }

    @Test
    public void shouldRejectNestedBodyExceedingOptimisticWidth()
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[512]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        generator.startMessage(10, 1).writeBytes(1, new byte[200]);

        assertThrows(ProtobufException.class, generator::endMessage);
    }

    private static byte[] bytes(
        MutableDirectBufferEx buffer,
        int length)
    {
        byte[] result = new byte[length];
        buffer.getBytes(0, result);
        return result;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("M")
                .field(field(1, "x", ProtobufType.INT32))
                .build())
            .message(ProtobufMessage.builder("All")
                .field(field(1, "i32", ProtobufType.INT32))
                .field(field(2, "si32", ProtobufType.SINT32))
                .field(field(3, "u32", ProtobufType.UINT32))
                .field(field(4, "fx32", ProtobufType.FIXED32))
                .field(field(5, "d", ProtobufType.DOUBLE))
                .field(field(6, "b", ProtobufType.BOOL))
                .field(field(7, "s", ProtobufType.STRING))
                .field(field(8, "by", ProtobufType.BYTES))
                .field(ProtobufField.builder().number(9).name("e").type(ProtobufType.ENUM).typeName("Color").build())
                .field(ProtobufField.builder().number(10).name("m").type(ProtobufType.MESSAGE).typeName("M").build())
                .field(ProtobufField.builder().number(11).name("g").type(ProtobufType.GROUP).typeName("M").build())
                .build())
            .build();
    }

    private static ProtobufField field(
        int number,
        String name,
        ProtobufType type)
    {
        return ProtobufField.builder().number(number).name(name).type(type).build();
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
            case START_GROUP:
                events.add("(");
                depth++;
                break;
            case END_GROUP:
                events.add(")");
                depth--;
                break;
            case FIELD:
                events.add("F" + source.field().number());
                break;
            case VALUE:
                events.add("V" + value(source));
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
            default:
                value = Long.toString(source.longValue());
                break;
            }
            return value;
        }
    }

    private static final class Mirror implements ProtobufSink
    {
        private final ProtobufGenerator generator;
        private int depth;
        private ProtobufField pending;

        private Mirror(
            ProtobufGenerator generator)
        {
            this.generator = generator;
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
                depth++;
                if (depth > 1)
                {
                    generator.startMessage(pending.number(), source.segment().capacity());
                }
                break;
            case FIELD:
                pending = source.field();
                break;
            case VALUE:
                writeValue(source);
                break;
            case END_MESSAGE:
                if (depth > 1)
                {
                    generator.endMessage();
                }
                depth--;
                if (depth == 0)
                {
                    status = ProtobufPipeline.Status.COMPLETED;
                }
                break;
            default:
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            depth = 0;
            pending = null;
        }

        private void writeValue(
            ProtobufSource source)
        {
            ProtobufField field = source.field();
            int number = field.number();
            switch (field.type())
            {
            case INT32:
                generator.writeInt32(number, (int) source.longValue());
                break;
            case INT64:
                generator.writeInt64(number, source.longValue());
                break;
            case UINT32:
                generator.writeUInt32(number, (int) source.longValue());
                break;
            case UINT64:
                generator.writeUInt64(number, source.longValue());
                break;
            case SINT32:
                generator.writeSInt32(number, (int) source.longValue());
                break;
            case SINT64:
                generator.writeSInt64(number, source.longValue());
                break;
            case FIXED32:
                generator.writeFixed32(number, (int) source.longValue());
                break;
            case FIXED64:
                generator.writeFixed64(number, source.longValue());
                break;
            case SFIXED32:
                generator.writeSFixed32(number, (int) source.longValue());
                break;
            case SFIXED64:
                generator.writeSFixed64(number, source.longValue());
                break;
            case FLOAT:
                generator.writeFloat(number, source.floatValue());
                break;
            case DOUBLE:
                generator.writeDouble(number, source.doubleValue());
                break;
            case BOOL:
                generator.writeBool(number, source.longValue() != 0L);
                break;
            case ENUM:
                generator.writeEnum(number, (int) source.longValue());
                break;
            case STRING:
                byte[] text = new byte[source.segment().capacity()];
                source.segment().getBytes(0, text);
                generator.writeString(number, new String(text, UTF_8));
                break;
            case BYTES:
                generator.writeBytes(number, source.segment(), 0, source.segment().capacity());
                break;
            default:
                break;
            }
        }
    }
}
