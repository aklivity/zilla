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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ConformanceTesteeTest
{
    private final ConformanceTestee testee = new ConformanceTestee(Protobuf.schema()
        .message(ProtobufMessage.builder("conformance.TestMessage")
            .field(ProtobufField.builder().number(1).name("a").type(ProtobufType.STRING).build())
            .field(ProtobufField.builder().number(2).name("b").type(ProtobufType.INT32).build())
            .build())
        .build());

    @Test
    public void shouldCanonicalizeBinaryRequest()
    {
        byte[] payload = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
        });
        byte[] request = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes("conformance.TestMessage".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(payload);
        });

        byte[] response = testee.handle(request);

        byte[] expected = new byte[]{0x0a, 0x02, 'h', 'i', 0x10, 0x05};
        assertArrayEquals(expected, field(response, 3));
    }

    @Test
    public void shouldSkipJsonRequest()
    {
        byte[] request = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes("conformance.TestMessage".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(2);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("{}".getBytes(UTF_8));
        });

        byte[] response = testee.handle(request);

        assertTrue(field(response, 5) != null, "expected skipped result");
    }

    @Test
    public void shouldSkipUnknownMessage()
    {
        byte[] request = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes("conformance.Nope".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(new byte[0]);
        });

        byte[] response = testee.handle(request);

        assertEquals("unknown message conformance.Nope", new String(field(response, 5), UTF_8));
    }

    @Test
    public void shouldReportParseErrorForMalformedPayload()
    {
        byte[] request = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes("conformance.TestMessage".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(new byte[]{(byte) 0x10, (byte) 0x80});
        });

        byte[] response = testee.handle(request);

        assertTrue(field(response, 1) != null, "expected parse_error result");
    }

    private static byte[] field(
        byte[] message,
        int number)
    {
        ProtobufReader reader = new ProtobufReader().wrap(new UnsafeBuffer(message), 0, message.length);
        byte[] value = null;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int fieldNumber = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (fieldNumber == number && wireType == ProtobufWireType.LEN)
            {
                int length = reader.readLength();
                value = new byte[length];
                reader.buffer().getBytes(reader.offset(), value);
                reader.skip(length);
            }
            else
            {
                reader.skipField(fieldNumber, wireType);
            }
        }
        return value;
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
}
