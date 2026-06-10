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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufToJson;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;
import io.aklivity.zilla.runtime.common.protobuf.StreamingProtobuf;

public class ProtobufDecoderEdgeTest
{
    @Test
    public void shouldEmitMapEntryDefaultWhenValueMissing()
    {
        ProtobufSchema schema = StreamingProtobuf.schema()
            .message(ProtobufMessage.builder("M.LabelsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("M")
                .field(ProtobufField.builder().number(1).name("labels").type(ProtobufType.MESSAGE)
                    .typeName("M.LabelsEntry").repeated(true).build())
                .build())
            .build();

        MutableDirectBuffer entry = new UnsafeBuffer(new byte[32]);
        ProtobufWriter entryWriter = new ProtobufWriter().wrap(entry, 0);
        entryWriter.writeTag(1, ProtobufWireType.LEN);
        entryWriter.writeBytes("k".getBytes(UTF_8));

        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(1, ProtobufWireType.LEN);
        writer.writeBytes(entry, 0, entryWriter.length());

        assertEquals("{\"labels\":{\"k\":0}}", toJson(schema, "M", wire, writer.length()));
    }

    @Test
    public void shouldDecodeUnknownEnumValueAsNumber()
    {
        ProtobufSchema schema = StreamingProtobuf.schema()
            .enumeration(ProtobufEnum.builder("Color").value("RED", 0).build())
            .message(ProtobufMessage.builder("M")
                .field(ProtobufField.builder().number(1).name("color").type(ProtobufType.ENUM)
                    .typeName("Color").build())
                .build())
            .build();

        MutableDirectBuffer wire = new UnsafeBuffer(new byte[16]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(1, ProtobufWireType.VARINT);
        writer.writeVarint64(42);

        assertEquals("{\"color\":42}", toJson(schema, "M", wire, writer.length()));
    }

    @Test
    public void shouldDecodeMapWithMessageValue()
    {
        ProtobufSchema schema = StreamingProtobuf.schema()
            .message(ProtobufMessage.builder("Point")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("M.PointsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.MESSAGE)
                    .typeName("Point").build())
                .build())
            .message(ProtobufMessage.builder("M")
                .field(ProtobufField.builder().number(1).name("points").type(ProtobufType.MESSAGE)
                    .typeName("M.PointsEntry").repeated(true).build())
                .build())
            .build();

        MutableDirectBuffer point = new UnsafeBuffer(new byte[16]);
        ProtobufWriter pointWriter = new ProtobufWriter().wrap(point, 0);
        pointWriter.writeTag(1, ProtobufWireType.VARINT);
        pointWriter.writeVarint64(9);

        MutableDirectBuffer entry = new UnsafeBuffer(new byte[32]);
        ProtobufWriter entryWriter = new ProtobufWriter().wrap(entry, 0);
        entryWriter.writeTag(1, ProtobufWireType.LEN);
        entryWriter.writeBytes("a".getBytes(UTF_8));
        entryWriter.writeTag(2, ProtobufWireType.LEN);
        entryWriter.writeBytes(point, 0, pointWriter.length());

        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(1, ProtobufWireType.LEN);
        writer.writeBytes(entry, 0, entryWriter.length());

        assertEquals("{\"points\":{\"a\":{\"x\":9}}}", toJson(schema, "M", wire, writer.length()));
    }

    @Test
    public void shouldEmitDefaultMessageWhenMapValueMissing()
    {
        ProtobufSchema schema = StreamingProtobuf.schema()
            .message(ProtobufMessage.builder("Point")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("M.PointsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.MESSAGE)
                    .typeName("Point").build())
                .build())
            .message(ProtobufMessage.builder("M")
                .field(ProtobufField.builder().number(1).name("points").type(ProtobufType.MESSAGE)
                    .typeName("M.PointsEntry").repeated(true).build())
                .build())
            .build();

        MutableDirectBuffer entry = new UnsafeBuffer(new byte[16]);
        ProtobufWriter entryWriter = new ProtobufWriter().wrap(entry, 0);
        entryWriter.writeTag(1, ProtobufWireType.LEN);
        entryWriter.writeBytes("a".getBytes(UTF_8));

        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(1, ProtobufWireType.LEN);
        writer.writeBytes(entry, 0, entryWriter.length());

        assertEquals("{\"points\":{\"a\":{}}}", toJson(schema, "M", wire, writer.length()));
    }

    private String toJson(
        ProtobufSchema schema,
        String messageName,
        MutableDirectBuffer wire,
        int length)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        ProtobufToJson decoder = StreamingProtobuf.protobufToJson(schema);
        int jsonLength = decoder.convert(messageName, wire, 0, length, out, 0);
        return out.getStringWithoutLengthUtf8(0, jsonLength);
    }
}
