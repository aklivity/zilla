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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
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
        MutableDirectBuffer nested = new UnsafeBuffer(new byte[64]);
        ProtobufGenerator inner = Protobuf.generator().wrap(nested, 0);
        inner.writeInt32(1, 9);
        int nestedLength = inner.length();

        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0);
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
        ProtobufPipeline pipeline = Protobuf.parser(schema, "All").stream().into(sink);
        pipeline.reset();
        ProtobufPipeline.Status status = pipeline.feed(out, 0, generator.length());

        assertEquals(ProtobufPipeline.Status.COMPLETE, status);
        assertEquals(List.of("{",
            "F1", "V-5", "F2", "V-6", "F3", "V4000000000", "F4", "V7", "F5", "V1.5",
            "F6", "V1", "F7", "Vhi", "F8", "Vb3", "F9", "V1", "F10", "{", "F1", "V9", "}",
            "}"), sink.events);
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
        public ProtobufPipeline.Status feed(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            ProtobufPipeline.Status status = ProtobufPipeline.Status.PENDING;
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
                    status = ProtobufPipeline.Status.COMPLETE;
                }
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
            default:
                value = Long.toString(source.longValue());
                break;
            }
            return value;
        }
    }
}
