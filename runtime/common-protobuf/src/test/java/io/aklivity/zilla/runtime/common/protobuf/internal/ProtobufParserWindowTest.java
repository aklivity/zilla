/*
 * Copyright 2021-2026 Aklivity Inc
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParsingException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Drives the parser through a window that can never hold more than a fixed number of bytes, the way a caller
 * with a bounded decode buffer does: the unconsumed tail is retained at the front of the next window, and a
 * full window in which no byte was consumed can never make progress. A message whose nested body, packed
 * block or segment is larger than that window must still parse, because each is consumed incrementally.
 */
public class ProtobufParserWindowTest
{
    private static final int[] SLOTS = {16, 24, 32, 40};

    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldParseNestedMessageLargerThanWindow()
    {
        byte[] address = address("x".repeat(40), "Zion");
        byte[] message = person(address);
        List<String> expected = drain(Protobuf.parser(schema, "Person").wrap(new UnsafeBufferEx(message), 0, message.length));

        for (int slot : SLOTS)
        {
            List<String> events = new ArrayList<>();
            StringBuilder pending = new StringBuilder();
            int[] depth = {0};
            driveBounded(Protobuf.parser(schema, "Person"), message, slot, false, (parser, event) ->
            {
                record(parser, events, pending, event);
                if (event == ProtobufEvent.START_MESSAGE && depth[0]++ > 0)
                {
                    assertTrue(parser.segment().capacity() <= parser.remaining(), "segment beyond window " + slot);
                    assertEquals(address.length, parser.segment().capacity() + parser.deferredBytes(), "window " + slot);
                }
            });

            assertEquals(expected, events, "window " + slot);
        }
    }

    @Test
    public void shouldParsePackedBlockLargerThanWindow()
    {
        byte[] packed = new byte[30];
        for (int i = 0; i < packed.length; i++)
        {
            packed[i] = (byte) (i + 1);
        }
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Alice".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.LEN);
            w.writeBytes(packed);
        });
        List<String> expected = drain(Protobuf.parser(schema, "Person").wrap(new UnsafeBufferEx(message), 0, message.length));

        for (int slot : SLOTS)
        {
            List<String> events = new ArrayList<>();
            StringBuilder pending = new StringBuilder();
            driveBounded(Protobuf.parser(schema, "Person"), message, slot, false,
                (parser, event) -> record(parser, events, pending, event));

            assertEquals(expected, events, "window " + slot);
        }
    }

    @Test
    public void shouldParseGroupLargerThanWindow()
    {
        byte[] message = record(groupBody());
        List<String> expected = drain(Protobuf.parser(schema, "Record").wrap(new UnsafeBufferEx(message), 0, message.length));

        for (int slot : SLOTS)
        {
            List<String> events = new ArrayList<>();
            StringBuilder pending = new StringBuilder();
            driveBounded(Protobuf.parser(schema, "Record"), message, slot, false,
                (parser, event) -> record(parser, events, pending, event));

            assertEquals(expected, events, "window " + slot);
        }
    }

    @Test
    public void shouldStreamNestedMessageSegmentLargerThanWindow()
    {
        byte[] address = address("x".repeat(40), "Zion");
        byte[] message = person(address);

        for (int slot : SLOTS)
        {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            List<Integer> lengths = new ArrayList<>();
            List<Integer> deferreds = new ArrayList<>();
            driveBounded(Protobuf.parser(schema, "Person"), message, slot, true, (parser, event) ->
            {
                if (event == ProtobufEvent.SEGMENT)
                {
                    lengths.add(parser.segment().capacity());
                    deferreds.add(parser.deferredBytes());
                    appendSegment(parser, body);
                }
            });

            assertArrayEquals(address, body.toByteArray(), "window " + slot);
            assertTrue(deferreds.size() > 1, "expected multiple segments at window " + slot);
            int delivered = 0;
            for (int i = 0; i < deferreds.size(); i++)
            {
                delivered += lengths.get(i);
                assertEquals(address.length - delivered, (int) deferreds.get(i), "deferred at segment " + i + ", window " + slot);
            }
        }
    }

    @Test
    public void shouldStreamGroupSegmentLargerThanWindow()
    {
        byte[] body = groupBody();
        byte[] message = record(body);

        for (int slot : SLOTS)
        {
            ByteArrayOutputStream segments = new ByteArrayOutputStream();
            List<Integer> deferreds = new ArrayList<>();
            driveBounded(Protobuf.parser(schema, "Record"), message, slot, true, (parser, event) ->
            {
                if (event == ProtobufEvent.SEGMENT)
                {
                    deferreds.add(parser.deferredBytes());
                    appendSegment(parser, segments);
                }
            });

            assertArrayEquals(body, segments.toByteArray(), "window " + slot);
            assertTrue(deferreds.size() > 1, "expected multiple segments at window " + slot);
            assertEquals(0, (int) deferreds.get(deferreds.size() - 1), "last segment at window " + slot);
            for (int i = 0; i < deferreds.size() - 1; i++)
            {
                assertTrue(deferreds.get(i) > 0, "segment " + i + " is not last at window " + slot);
            }
        }
    }

    @Test
    public void shouldRejectUnterminatedGroupSegment()
    {
        byte[] body = groupBody();
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.SGROUP);
            w.writeRaw(new UnsafeBufferEx(body), 0, body.length);
        });

        ProtobufParsingException ex = assertThrows(ProtobufParsingException.class, () ->
            driveBounded(Protobuf.parser(schema, "Record"), message, 32, true, (parser, event) -> {}));

        assertTrue(ex.getMessage().contains("unterminated group"), ex.getMessage());
    }

    private byte[] person(
        byte[] address)
    {
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Alice".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(address);
        });
    }

    private byte[] address(
        String street,
        String city)
    {
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(street.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(city.getBytes(UTF_8));
        });
    }

    // the blob holds 0x14, the end-group tag of field 2, so a scan that stops at the first such byte ends early
    private byte[] groupBody()
    {
        String blob = "ab" + (char) 0x14 + "cd" + "z".repeat(50);
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(blob.getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.SGROUP);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("inner".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.EGROUP);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("end".getBytes(UTF_8));
        });
    }

    private byte[] record(
        byte[] body)
    {
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.SGROUP);
            w.writeRaw(new UnsafeBufferEx(body), 0, body.length);
            w.writeTag(2, ProtobufWireType.EGROUP);
        });
    }

    // feeds at most slot bytes per window, retaining the unconsumed tail at the front of the next; a full window
    // in which the parser consumed nothing cannot ever progress, so that is reported rather than looped on
    private static void driveBounded(
        ProtobufParser parser,
        byte[] message,
        int slot,
        boolean segmented,
        BiConsumer<ProtobufParser, ProtobufEvent> consumer)
    {
        byte[] retained = new byte[0];
        int progress = 0;
        boolean started = false;
        boolean segmentNext = false;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(slot - retained.length, message.length - progress);
            byte[] buffer = new byte[retained.length + take];
            System.arraycopy(retained, 0, buffer, 0, retained.length);
            System.arraycopy(message, progress, buffer, retained.length, take);
            progress += take;
            boolean last = progress >= message.length;
            UnsafeBufferEx view = new UnsafeBufferEx(buffer);
            if (started)
            {
                parser.resume(view, 0, buffer.length, last);
            }
            else
            {
                parser.wrap(view, 0, buffer.length, last);
                started = true;
            }

            boolean starved = false;
            while (parser.hasNext() && !starved)
            {
                ProtobufEvent event = parser.nextEvent(segmentNext
                    ? ProtobufParser.Mode.SEGMENTED
                    : ProtobufParser.Mode.STRUCTURED);
                if (event == null)
                {
                    starved = true;
                }
                else
                {
                    segmentNext = segmented && event == ProtobufEvent.FIELD && parser.field().composite();
                    consumer.accept(parser, event);
                }
            }

            if (parser.hasNext())
            {
                int consumed = buffer.length - parser.remaining();
                assertTrue(consumed > 0 || buffer.length < slot,
                    "stalled: window of " + slot + " bytes is full and no input was consumed");
                retained = Arrays.copyOfRange(buffer, consumed, buffer.length);
            }
            else
            {
                done = true;
            }
        }
    }

    private static List<String> drain(
        ProtobufParser parser)
    {
        List<String> events = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        while (parser.hasNext())
        {
            record(parser, events, pending, parser.nextEvent());
        }
        return events;
    }

    private static void record(
        ProtobufParser parser,
        List<String> events,
        StringBuilder pending,
        ProtobufEvent event)
    {
        switch (event)
        {
        case START_MESSAGE:
            events.add("{");
            break;
        case END_MESSAGE:
            events.add("}");
            break;
        case START_GROUP:
            events.add("<");
            break;
        case END_GROUP:
            events.add(">");
            break;
        case FIELD:
            events.add("F" + parser.field().number());
            break;
        case VALUE:
            pending.append(scalar(parser));
            if (parser.deferredBytes() == 0)
            {
                events.add("V" + pending);
                pending.setLength(0);
            }
            break;
        default:
            break;
        }
    }

    private static String scalar(
        ProtobufParser parser)
    {
        String value;
        if (parser.field().type() == ProtobufType.STRING)
        {
            byte[] text = new byte[parser.segment().capacity()];
            parser.segment().getBytes(0, text);
            value = new String(text, UTF_8);
        }
        else
        {
            value = Long.toString(parser.longValue());
        }
        return value;
    }

    private static void appendSegment(
        ProtobufParser parser,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[parser.segment().capacity()];
        parser.segment().getBytes(0, chunk);
        sink.writeBytes(chunk);
    }

    private static byte[] wire(
        Consumer<ProtobufWriter> body)
    {
        ExpandableArrayBufferEx buffer = new ExpandableArrayBufferEx();
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
                .field(ProtobufField.builder().number(2).name("address").type(ProtobufType.MESSAGE)
                    .typeName("Address").build())
                .field(ProtobufField.builder().number(3).name("scores").type(ProtobufType.INT32)
                    .repeated(true).packed(true).build())
                .build())
            .message(ProtobufMessage.builder("Grp")
                .field(ProtobufField.builder().number(1).name("blob").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("label").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Record")
                .field(ProtobufField.builder().number(1).name("note").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("grp").type(ProtobufType.GROUP).typeName("Grp").build())
                .build())
            .build();
    }
}
