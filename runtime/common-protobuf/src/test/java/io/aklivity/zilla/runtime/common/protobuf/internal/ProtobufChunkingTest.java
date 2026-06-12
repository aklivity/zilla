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
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Drives the streaming sink against a hard, undersized output limit so a single logical message is
 * emitted as several flow-control chunks. The chunks are byte-wise fragments of one stream: concatenated
 * and decoded, the per-record fragments merge (by protobuf message-merge semantics) back into the original
 * message. A nested message that simply does not fit splits at field boundaries (closing and reopening the
 * record per chunk); a length-delimited value larger than a whole chunk is fragmented mid-byte and streamed
 * across chunks, never buffered in full.
 */
public class ProtobufChunkingTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldChunkNestedMessageAcrossChunks()
    {
        byte[] input = person("neo", "123 Main Street", "Zion");

        List<byte[]> chunks = chunk(input, 20);

        assertTrue(chunks.size() >= 2, "expected the nested message to be split across chunks");
        Person person = decode(concat(chunks));
        assertEquals("neo", person.name);
        assertEquals("123 Main Street", person.street);
        assertEquals("Zion", person.city);
    }

    @Test
    public void shouldFragmentLeafLargerThanChunk()
    {
        String street = "x".repeat(200);
        byte[] input = person("neo", street, "Zion");

        List<byte[]> chunks = chunk(input, 40);

        assertTrue(chunks.size() >= 5, "expected the long value to be fragmented across many chunks");
        Person person = decode(concat(chunks));
        assertEquals("neo", person.name);
        assertEquals(street, person.street);
        assertEquals("Zion", person.city);
    }

    @Test
    public void shouldFragmentLeafLargerThanWholeBuffer()
    {
        String street = "y".repeat(500);
        byte[] input = person("neo", street, "Springfield");

        // the buffer (64) is far smaller than the value (500) — it can only ever hold a slice of it
        List<byte[]> chunks = chunk(input, 64);

        Person person = decode(concat(chunks));
        assertEquals("neo", person.name);
        assertEquals(street, person.street);
        assertEquals("Springfield", person.city);
    }

    @Test
    public void shouldFragmentLeafInsideGroupSpanningChunks()
    {
        ProtobufSchema grouped = Protobuf.schema()
            .message(ProtobufMessage.builder("Grp")
                .field(ProtobufField.builder().number(1).name("blob").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("label").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Record")
                .field(ProtobufField.builder().number(1).name("note").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("grp").type(ProtobufType.GROUP).typeName("Grp").build())
                .build())
            .build();

        String blob = "z".repeat(300);
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.SGROUP);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(blob.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("end".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.EGROUP);
        });

        int limit = 48;
        MutableDirectBuffer out = new UnsafeBuffer(new byte[1024]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, limit);
        ProtobufPipeline pipeline = Protobuf.parser(grouped, "Record").stream()
            .into(ProtobufSink.of(generator, grouped, "Record"));
        pipeline.reset();

        List<byte[]> chunks = new ArrayList<>();
        UnsafeBuffer buffer = new UnsafeBuffer(input);
        ProtobufPipeline.Status status = pipeline.feed(buffer, 0, input.length);
        while (status == ProtobufPipeline.Status.SUSPENDED)
        {
            assertTrue(generator.length() <= limit, "chunk exceeded the generator limit");
            chunks.add(bytes(out, generator.length()));
            generator.wrap(out, 0, limit);
            status = pipeline.feed(buffer, 0, input.length);
        }
        assertEquals(ProtobufPipeline.Status.COMPLETED, status);
        chunks.add(bytes(out, generator.length()));

        // decode the concatenated stream, merging the repeated group occurrences as the wire merges them
        ProtobufParser parser = Protobuf.parser(grouped, "Record")
            .wrap(new UnsafeBuffer(concat(chunks)), 0, concat(chunks).length);
        String note = null;
        String decodedBlob = null;
        String label = null;
        ProtobufField pending = null;
        int depth = 0;
        while (parser.hasNext())
        {
            switch (parser.nextEvent())
            {
            case START_MESSAGE:
            case START_GROUP:
                depth++;
                break;
            case END_MESSAGE:
            case END_GROUP:
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
                    note = text;
                }
                else if (depth == 2 && pending.number() == 1)
                {
                    decodedBlob = text;
                }
                else if (depth == 2 && pending.number() == 2)
                {
                    label = text;
                }
                break;
            default:
                break;
            }
        }

        assertTrue(chunks.size() >= 5, "expected the group's value to be fragmented across many chunks");
        assertEquals("hi", note);
        assertEquals(blob, decodedBlob);
        assertEquals("end", label);
    }

    @Test
    public void shouldFeedEachEventOnceThroughTransformAcrossSuspends()
    {
        byte[] input = person("neo", "x".repeat(200), "Zion");

        // a transform must see the same event sequence regardless of suspends: resume continues at the
        // sink without replaying the event through the stages, so no event is delivered twice
        List<ProtobufEvent> whole = recordEvents(input, 4096);
        List<ProtobufEvent> bounded = recordEvents(input, 40);

        assertEquals(whole, bounded);
    }

    private List<ProtobufEvent> recordEvents(
        byte[] input,
        int limit)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[4096]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, limit);
        List<ProtobufEvent> events = new ArrayList<>();
        ProtobufTransform recorder = (control, source, event, sink) ->
        {
            events.add(event);
            return sink.feed(control, source, event);
        };
        ProtobufPipeline pipeline = Protobuf.parser(schema, "Person").stream()
            .transform(recorder)
            .into(ProtobufSink.of(generator, schema, "Person"));
        pipeline.reset();

        UnsafeBuffer buffer = new UnsafeBuffer(input);
        ProtobufPipeline.Status status = pipeline.feed(buffer, 0, input.length);
        while (status == ProtobufPipeline.Status.SUSPENDED)
        {
            generator.wrap(out, 0, limit);
            status = pipeline.feed(buffer, 0, input.length);
        }
        assertEquals(ProtobufPipeline.Status.COMPLETED, status);
        return events;
    }

    private List<byte[]> chunk(
        byte[] input,
        int limit)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[1024]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, limit);
        ProtobufPipeline pipeline = Protobuf.parser(schema, "Person").stream()
            .into(ProtobufSink.of(generator, schema, "Person"));
        pipeline.reset();

        List<byte[]> chunks = new ArrayList<>();
        UnsafeBuffer buffer = new UnsafeBuffer(input);
        ProtobufPipeline.Status status = pipeline.feed(buffer, 0, input.length);
        while (status == ProtobufPipeline.Status.SUSPENDED)
        {
            assertTrue(generator.length() <= limit, "chunk exceeded the generator limit");
            chunks.add(bytes(out, generator.length()));
            generator.wrap(out, 0, limit);
            status = pipeline.feed(buffer, 0, input.length);
        }
        assertEquals(ProtobufPipeline.Status.COMPLETED, status);
        assertTrue(generator.length() <= limit, "chunk exceeded the generator limit");
        chunks.add(bytes(out, generator.length()));
        return chunks;
    }

    // Decodes the concatenated chunks, assigning each scalar to the original logical Person — the repeated
    // home (field 2) occurrences write into the same fields, as a message-merge decoder would coalesce them.
    private Person decode(
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

    private byte[] person(
        String name,
        String street,
        String city)
    {
        byte[] address = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(street.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(city.getBytes(UTF_8));
        });
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(address);
        });
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
