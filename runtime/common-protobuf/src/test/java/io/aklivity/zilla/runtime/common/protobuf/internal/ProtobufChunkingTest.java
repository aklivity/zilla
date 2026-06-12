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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Drives a hand-coded {@code parser + generator} transform loop (no pipeline) that, on a bounded
 * generator, drains at a field boundary when the output fills: it calls {@code flush()} to close the
 * open levels into a chunk and re-wraps a fresh buffer, with the generator reopening the levels itself.
 * The chunks are independent, mergeable records; concatenated and decoded they reassemble (by protobuf
 * message-merge semantics) into the original message. A pipeline-driven variant exercises the same
 * generator through the wire sink and the {@code SUSPENDED} loop.
 */
public class ProtobufChunkingTest
{
    private static final int HEADROOM = 20;

    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldChunkNestedMessageAcrossSuspends()
    {
        byte[] address = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("123 Main Street".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(address);
        });

        int limit = 40;
        List<byte[]> chunks = transformChunked(input, limit);

        assertTrue(chunks.size() >= 2, "expected the nested message to be split across chunks");
        for (byte[] chunk : chunks)
        {
            assertTrue(chunk.length <= limit, "chunk exceeded the generator limit");
        }

        Person person = decodeMerged(concat(chunks));
        assertEquals("neo", person.name);
        assertEquals("123 Main Street", person.street);
        assertEquals("Zion", person.city);
    }

    @Test
    public void shouldChunkViaPipelineSinkWhenStreaming()
    {
        byte[] address = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("123 Main Street".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("Springfield USA".getBytes(UTF_8));
        });
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(address);
        });

        int limit = 35;
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, limit);
        ProtobufPipeline pipeline = Protobuf.parser(schema, "Person").stream()
            .into(ProtobufSink.of(generator, schema, "Person"));
        pipeline.reset();

        List<byte[]> chunks = new ArrayList<>();
        ProtobufPipeline.Status status = pipeline.feed(new UnsafeBuffer(input), 0, input.length);
        while (status == ProtobufPipeline.Status.SUSPENDED)
        {
            chunks.add(bytes(out, generator.length()));
            generator.wrap(out, 0, limit);
            status = pipeline.feed(new UnsafeBuffer(input), 0, input.length);
        }
        assertEquals(ProtobufPipeline.Status.COMPLETE, status);
        chunks.add(bytes(out, generator.length()));

        assertTrue(chunks.size() >= 2, "expected the nested message to be split across chunks");
        for (byte[] chunk : chunks)
        {
            assertTrue(chunk.length <= limit, "chunk exceeded the generator limit");
        }

        Person person = decodeMerged(concat(chunks));
        assertEquals("neo", person.name);
        assertEquals("123 Main Street", person.street);
        assertEquals("Springfield USA", person.city);
    }

    // Identity Person -> Person transform driving a bounded generator: when the output nears its limit
    // at a field boundary it flushes a drainable chunk and re-wraps (the generator reopens the open
    // levels itself), so the loop holds no chunking state of its own.
    private List<byte[]> transformChunked(
        byte[] input,
        int limit)
    {
        ProtobufParser parser = Protobuf.parser(schema, "Person").wrap(new UnsafeBuffer(input), 0, input.length);
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, limit);

        List<byte[]> chunks = new ArrayList<>();
        ProtobufField pending = null;
        int depth = 0;

        while (parser.hasNext())
        {
            if (depth >= 1 && generator.length() > 0 && generator.remaining() < HEADROOM)
            {
                generator.flush();
                chunks.add(bytes(out, generator.length()));
                generator.wrap(out, 0, limit);
            }

            switch (parser.nextEvent())
            {
            case START_MESSAGE:
                if (depth > 0)
                {
                    generator.startMessage(pending.number(), parser.length());
                }
                depth++;
                break;
            case END_MESSAGE:
                depth--;
                if (depth > 0)
                {
                    generator.endMessage();
                }
                break;
            case FIELD:
                pending = parser.field();
                break;
            case VALUE:
                byte[] value = new byte[parser.length()];
                parser.buffer().getBytes(parser.offset(), value);
                generator.writeString(pending.number(), new String(value, UTF_8));
                break;
            default:
                break;
            }
        }
        chunks.add(bytes(out, generator.length()));
        return chunks;
    }

    // Decodes the concatenated chunks, merging the repeated home (field 2) occurrences as a singular
    // message field decoder would, to reconstruct the original logical Person.
    private Person decodeMerged(
        byte[] message)
    {
        ProtobufParser parser = Protobuf.parser(schema, "Person").wrap(new UnsafeBuffer(message), 0, message.length);
        Person person = new Person();
        ProtobufField pending = null;
        int depth = 0;
        while (parser.hasNext())
        {
            switch (parser.nextEvent())
            {
            case START_MESSAGE:
                depth++;
                break;
            case END_MESSAGE:
                depth--;
                break;
            case FIELD:
                pending = parser.field();
                break;
            case VALUE:
                byte[] value = new byte[parser.length()];
                parser.buffer().getBytes(parser.offset(), value);
                String text = new String(value, UTF_8);
                if (depth == 1 && pending.number() == 1)
                {
                    person.name = text;
                }
                else if (depth == 2 && pending.number() == 1)
                {
                    person.street = text;
                }
                else if (depth == 2 && pending.number() == 2)
                {
                    person.city = text;
                }
                break;
            default:
                break;
            }
        }
        return person;
    }

    private static byte[] concat(
        List<byte[]> chunks)
    {
        int length = 0;
        for (byte[] chunk : chunks)
        {
            length += chunk.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] chunk : chunks)
        {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    private static byte[] bytes(
        MutableDirectBuffer buffer,
        int length)
    {
        byte[] result = new byte[length];
        buffer.getBytes(0, result);
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
            .message(ProtobufMessage.builder("Address")
                .field(ProtobufField.builder().number(1).name("street").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("city").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Person")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("home").type(ProtobufType.MESSAGE).typeName("Address").build())
                .build())
            .build();
    }

    private static final class Person
    {
        private String name;
        private String street;
        private String city;
    }
}
