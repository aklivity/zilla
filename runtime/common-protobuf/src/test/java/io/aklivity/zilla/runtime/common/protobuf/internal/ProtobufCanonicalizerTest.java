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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufCanonicalizerTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldNormalizeOrderPackAndRetainUnknown()
    {
        byte[] input = wire(w ->
        {
            w.writeTag(99, ProtobufWireType.VARINT);
            w.writeVarint64(7);
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(2);
        });

        byte[] expected = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(3, ProtobufWireType.LEN);
            w.writeBytes(new byte[]{1, 2});
            w.writeTag(99, ProtobufWireType.VARINT);
            w.writeVarint64(7);
        });

        assertArrayEquals(expected, canonicalize("M", input));
    }

    @Test
    public void shouldBeIdempotent()
    {
        byte[] input = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
        });

        byte[] once = canonicalize("M", input);
        byte[] twice = canonicalize("M", once);
        assertArrayEquals(once, twice);
    }

    @Test
    public void shouldCanonicalizeNestedMessageAndMap()
    {
        byte[] child = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(9);
        });
        byte[] entry = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("k".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(3);
        });

        byte[] input = wire(w ->
        {
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes(entry);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(child);
        });

        byte[] expected = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(child);
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes(entry);
        });

        assertArrayEquals(expected, canonicalize("M", input));
    }

    @Test
    public void shouldCanonicalizeAllScalarKindsAndRepeatedStrings()
    {
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeZigzag32(-5);
            w.writeTag(2, ProtobufWireType.I32);
            w.writeFixed32(7);
            w.writeTag(3, ProtobufWireType.I32);
            w.writeFixed32(-7);
            w.writeTag(4, ProtobufWireType.I64);
            w.writeFixed64(8);
            w.writeTag(5, ProtobufWireType.I64);
            w.writeFixed64(-8);
            w.writeTag(6, ProtobufWireType.VARINT);
            w.writeZigzag64(-9);
            w.writeTag(7, ProtobufWireType.VARINT);
            w.writeVarint64(4000000000L);
            w.writeTag(8, ProtobufWireType.VARINT);
            w.writeVarint64(-1L);
            w.writeTag(9, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(10, ProtobufWireType.I64);
            w.writeFixed64(Double.doubleToLongBits(1.5));
            w.writeTag(11, ProtobufWireType.I32);
            w.writeFixed32(Float.floatToIntBits(0.25f));
            w.writeTag(12, ProtobufWireType.LEN);
            w.writeBytes(new byte[]{1, 2, 3});
            w.writeTag(13, ProtobufWireType.LEN);
            w.writeBytes("a".getBytes(UTF_8));
            w.writeTag(13, ProtobufWireType.LEN);
            w.writeBytes("b".getBytes(UTF_8));
        });

        byte[] once = canonicalize("S", input);
        byte[] twice = canonicalize("S", once);
        assertArrayEquals(once, twice);
    }

    @Test
    public void shouldRejectUnknownMessage()
    {
        assertThrows(ProtobufException.class, () -> canonicalize("Nope", new byte[0]));
    }

    @Test
    public void shouldRejectTruncatedVarint()
    {
        assertThrows(ProtobufException.class, () -> canonicalize("M", new byte[]{(byte) 0x10, (byte) 0x80}));
    }

    @Test
    public void shouldRejectTruncatedLengthDelimited()
    {
        assertThrows(ProtobufException.class,
            () -> canonicalize("M", new byte[]{(byte) 0x0a, (byte) 0x05, 'h', 'i'}));
    }

    @Test
    public void shouldRejectUnterminatedGroup()
    {
        assertThrows(ProtobufException.class, () -> canonicalize("M", new byte[]{(byte) 0x2b}));
    }

    @Test
    public void shouldRejectMismatchedGroupEnd()
    {
        assertThrows(ProtobufException.class, () -> canonicalize("M", new byte[]{(byte) 0x2b, (byte) 0x34}));
    }

    private byte[] canonicalize(
        String messageName,
        byte[] input)
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[256]);
        ProtobufCanonicalizer canonicalizer = new ProtobufCanonicalizer(schema);
        int length = canonicalizer.canonicalize(messageName, new UnsafeBufferEx(input), 0, input.length, out, 0);
        byte[] result = new byte[length];
        out.getBytes(0, result);
        return result;
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
            .message(ProtobufMessage.builder("Child")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("M.LabelsEntry")
                .mapEntry(true)
                .field(ProtobufField.builder().number(1).name("key").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("M")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(3).name("nums").type(ProtobufType.INT32).repeated(true).build())
                .field(ProtobufField.builder().number(4).name("child").type(ProtobufType.MESSAGE)
                    .typeName("Child").build())
                .field(ProtobufField.builder().number(5).name("labels").type(ProtobufType.MESSAGE)
                    .typeName("M.LabelsEntry").repeated(true).build())
                .build())
            .message(ProtobufMessage.builder("S")
                .field(ProtobufField.builder().number(1).name("sint32").type(ProtobufType.SINT32).build())
                .field(ProtobufField.builder().number(2).name("fix32").type(ProtobufType.FIXED32).build())
                .field(ProtobufField.builder().number(3).name("sfix32").type(ProtobufType.SFIXED32).build())
                .field(ProtobufField.builder().number(4).name("fix64").type(ProtobufType.FIXED64).build())
                .field(ProtobufField.builder().number(5).name("sfix64").type(ProtobufType.SFIXED64).build())
                .field(ProtobufField.builder().number(6).name("sint64").type(ProtobufType.SINT64).build())
                .field(ProtobufField.builder().number(7).name("u32").type(ProtobufType.UINT32).build())
                .field(ProtobufField.builder().number(8).name("u64").type(ProtobufType.UINT64).build())
                .field(ProtobufField.builder().number(9).name("flag").type(ProtobufType.BOOL).build())
                .field(ProtobufField.builder().number(10).name("d").type(ProtobufType.DOUBLE).build())
                .field(ProtobufField.builder().number(11).name("f").type(ProtobufType.FLOAT).build())
                .field(ProtobufField.builder().number(12).name("bytes").type(ProtobufType.BYTES).build())
                .field(ProtobufField.builder().number(13).name("tags").type(ProtobufType.STRING).repeated(true).build())
                .build())
            .build();
    }
}
