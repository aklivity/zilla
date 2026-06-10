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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufToJson;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;
import io.aklivity.zilla.runtime.common.protobuf.StreamingProtobuf;

public class ProtobufDecoderTest
{
    private final ProtobufSchema schema = StreamingProtobuf.schema()
        .message(ProtobufMessage.builder("M")
            .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
            .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
            .field(ProtobufField.builder().number(3).name("scores").type(ProtobufType.INT32).repeated(true).build())
            .build())
        .build();

    @Test
    public void shouldDecodeFieldsOutOfOrder()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(2, ProtobufWireType.VARINT);
        writer.writeVarint64(7);
        writer.writeTag(1, ProtobufWireType.LEN);
        writer.writeBytes("Neo".getBytes(UTF_8));

        assertEquals("{\"name\":\"Neo\",\"id\":7}", toJson(wire, writer.length()));
    }

    @Test
    public void shouldDecodeInterleavedRepeatedUnpacked()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(3, ProtobufWireType.VARINT);
        writer.writeVarint64(1);
        writer.writeTag(2, ProtobufWireType.VARINT);
        writer.writeVarint64(99);
        writer.writeTag(3, ProtobufWireType.VARINT);
        writer.writeVarint64(2);

        assertEquals("{\"id\":99,\"scores\":[1,2]}", toJson(wire, writer.length()));
    }

    @Test
    public void shouldDecodePackedRepeated()
    {
        MutableDirectBuffer block = new UnsafeBuffer(new byte[16]);
        ProtobufWriter blockWriter = new ProtobufWriter().wrap(block, 0);
        blockWriter.writeVarint64(10);
        blockWriter.writeVarint64(20);
        blockWriter.writeVarint64(30);

        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(3, ProtobufWireType.LEN);
        writer.writeBytes(block, 0, blockWriter.length());

        assertEquals("{\"scores\":[10,20,30]}", toJson(wire, writer.length()));
    }

    @Test
    public void shouldSkipUnknownField()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(1, ProtobufWireType.LEN);
        writer.writeBytes("Neo".getBytes(UTF_8));
        writer.writeTag(99, ProtobufWireType.VARINT);
        writer.writeVarint64(123);

        assertEquals("{\"name\":\"Neo\"}", toJson(wire, writer.length()));
    }

    @Test
    public void shouldRejectTruncatedVarint()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[]{(byte) 0x10, (byte) 0x80});

        assertThrows(ProtobufException.class, () -> toJson(wire, 2));
    }

    @Test
    public void shouldRejectTruncatedLengthDelimited()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[]{(byte) 0x0a, (byte) 0x05, 'h', 'i'});

        assertThrows(ProtobufException.class, () -> toJson(wire, 4));
    }

    @Test
    public void shouldRejectWireTypeMismatch()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[64]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(2, ProtobufWireType.LEN);
        writer.writeBytes("oops".getBytes(UTF_8));

        assertThrows(ProtobufException.class, () -> toJson(wire, writer.length()));
    }

    @Test
    public void shouldRejectGroupWireType()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[8]);
        ProtobufWriter writer = new ProtobufWriter().wrap(wire, 0);
        writer.writeTag(5, ProtobufWireType.SGROUP);

        assertThrows(ProtobufException.class, () -> toJson(wire, writer.length()));
    }

    @Test
    public void shouldRejectUnknownMessage()
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[1]);

        assertThrows(ProtobufException.class, () ->
            StreamingProtobuf.protobufToJson(schema).convert("Nope", wire, 0, 0, new UnsafeBuffer(new byte[16]), 0));
    }

    private String toJson(
        MutableDirectBuffer wire,
        int length)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        ProtobufToJson decoder = StreamingProtobuf.protobufToJson(schema);
        int jsonLength = decoder.convert("M", wire, 0, length, out, 0);
        return out.getStringWithoutLengthUtf8(0, jsonLength);
    }
}
