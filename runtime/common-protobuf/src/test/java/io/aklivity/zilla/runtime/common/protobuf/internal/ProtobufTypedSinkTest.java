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

import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufTypedSinkTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldReencodeSameSchema()
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
            w.writeVarint64(5);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        assertArrayEquals(message, transform("P", "P", message));
    }

    @Test
    public void shouldReencodeAllScalarTypes()
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

        assertArrayEquals(message, transform("S", "S", message));
    }

    @Test
    public void shouldTransformRenumberRenameAndDrop()
    {
        byte[] home = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("x".getBytes(UTF_8));
        });
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(9);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        byte[] expectedHome = wire(w ->
        {
            w.writeTag(9, ProtobufWireType.LEN);
            w.writeBytes("x".getBytes(UTF_8));
        });
        byte[] expected = wire(w ->
        {
            w.writeTag(7, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(8, ProtobufWireType.LEN);
            w.writeBytes(expectedHome);
        });

        assertArrayEquals(expected, transform("P", "P2", message));
    }

    @Test
    public void shouldReencodeGroup()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(5, ProtobufWireType.SGROUP);
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(9);
            w.writeTag(5, ProtobufWireType.EGROUP);
        });

        assertArrayEquals(message, transform("P", "P", message));
    }

    private byte[] transform(
        String readMessage,
        String writeMessage,
        byte[] message)
    {
        MutableDirectBuffer out = new UnsafeBufferEx(new byte[4096]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, readMessage))
            .into(ProtobufSink.of(generator, schema, writeMessage));
        pipeline.reset();

        Status status = pipeline.transform(new UnsafeBufferEx(message), 0, message.length);
        assertEquals(Status.COMPLETED, status);

        byte[] result = new byte[generator.length()];
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
            .message(ProtobufMessage.builder("Addr")
                .field(ProtobufField.builder().number(1).name("city").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Addr2")
                .field(ProtobufField.builder().number(9).name("city").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Grp")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("P")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(3).name("extra").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(4).name("home").type(ProtobufType.MESSAGE).typeName("Addr").build())
                .field(ProtobufField.builder().number(5).name("grp").type(ProtobufType.GROUP).typeName("Grp").build())
                .build())
            .message(ProtobufMessage.builder("P2")
                .field(ProtobufField.builder().number(7).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(8).name("home").type(ProtobufType.MESSAGE).typeName("Addr2").build())
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
}
