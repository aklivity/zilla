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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufParserTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldPullStructuredEvents()
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
            w.writeVarint64(7);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        ProtobufParser parser = Protobuf.parser(schema, "P").wrap(new UnsafeBufferEx(message), 0, message.length);

        List<String> events = new ArrayList<>();
        int rootLength = -1;
        int nestedLength = -1;
        String rootName = null;
        String nestedName = null;
        int depth = 0;
        while (parser.hasNext())
        {
            ProtobufEvent event = parser.nextEvent();
            switch (event)
            {
            case START_MESSAGE:
                events.add("{");
                if (depth == 0)
                {
                    rootLength = parser.segment().capacity();
                    rootName = parser.message().name();
                }
                else
                {
                    nestedLength = parser.segment().capacity();
                    nestedName = parser.message().name();
                }
                depth++;
                break;
            case END_MESSAGE:
                events.add("}");
                depth--;
                break;
            case FIELD:
                events.add("F" + parser.field().number());
                break;
            case VALUE:
                events.add("V" + scalar(parser));
                break;
            default:
                break;
            }
        }

        assertEquals(List.of("{", "F1", "Vneo", "F2", "V7", "F4", "{", "F1", "VZion", "}", "}"), events);
        assertEquals(message.length, rootLength);
        assertEquals(home.length, nestedLength);
        assertEquals("P", rootName);
        assertEquals("Addr", nestedName);
    }

    @Test
    public void shouldPullCompositeAsSegment()
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
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        ProtobufParser parser = Protobuf.parser(schema, "P").wrap(new UnsafeBufferEx(message), 0, message.length);

        List<String> events = new ArrayList<>();
        byte[] segment = null;
        boolean segmentNext = false;
        while (parser.hasNext())
        {
            // at the composite field, pull its value as a raw segment instead of recursing into it
            ProtobufEvent event = parser.nextEvent(segmentNext
                ? ProtobufParser.Mode.SEGMENTED
                : ProtobufParser.Mode.STRUCTURED);
            segmentNext = false;
            switch (event)
            {
            case START_MESSAGE:
                events.add("{");
                break;
            case END_MESSAGE:
                events.add("}");
                break;
            case FIELD:
                events.add("F" + parser.field().number());
                segmentNext = parser.field().composite();
                break;
            case VALUE:
                events.add("V" + scalar(parser));
                break;
            case SEGMENT:
                events.add("S" + parser.deferredBytes());
                segment = new byte[parser.segment().capacity()];
                parser.segment().getBytes(0, segment);
                break;
            default:
                break;
            }
        }

        assertEquals(List.of("{", "F1", "Vneo", "F4", "S0", "}"), events);
        assertArrayEquals(home, segment);
    }

    @Test
    public void shouldRejectUnresolvedNestedMessage()
    {
        ProtobufSchema broken = Protobuf.schema()
            .message(ProtobufMessage.builder("P")
                .field(ProtobufField.builder().number(4).name("home").type(ProtobufType.MESSAGE).typeName("Missing").build())
                .build())
            .build();
        byte[] message = wire(w ->
        {
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(new byte[]{0});
        });

        ProtobufParser parser = Protobuf.parser(broken, "P").wrap(new UnsafeBufferEx(message), 0, message.length);
        parser.nextEvent();
        parser.nextEvent();

        assertThrows(ProtobufException.class, parser::nextEvent);
    }

    @Test
    public void shouldPullSchemaFreeEvents()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
        });

        ProtobufParser parser = Protobuf.parser().wrap(new UnsafeBufferEx(message), 0, message.length);

        List<String> events = new ArrayList<>();
        while (parser.hasNext())
        {
            ProtobufEvent event = parser.nextEvent();
            switch (event)
            {
            case START_MESSAGE:
                events.add("{");
                break;
            case END_MESSAGE:
                events.add("}");
                break;
            case FIELD:
                events.add("F" + parser.fieldNumber() + ":" + parser.wireType());
                break;
            case VALUE:
                events.add(parser.wireType() == ProtobufWireType.LEN
                    ? "L" + parser.segment().capacity()
                    : "V" + parser.longValue());
                break;
            default:
                break;
            }
        }

        assertEquals(List.of("{", "F1:VARINT", "V5", "F2:LEN", "L2", "}"), events);
    }

    @Test
    public void shouldRejectUnknownRootMessageOnFirstEvent()
    {
        ProtobufParser parser = Protobuf.parser(schema, "Nope").wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

        assertThrows(ProtobufException.class, parser::nextEvent);
    }

    @Test
    public void shouldReuseAcrossMessages()
    {
        byte[] message = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });

        ProtobufParser parser = Protobuf.parser(schema, "P");

        assertEquals(4, count(parser, message));
        assertEquals(4, count(parser, message));
    }

    @Test
    public void shouldReturnNullThenResumeAcrossWindows()
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
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(home);
        });

        List<String> whole = drain(Protobuf.parser(schema, "P").wrap(new UnsafeBufferEx(message), 0, message.length));

        ProtobufParser parser = Protobuf.parser(schema, "P");
        List<String> events = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        boolean[] sawNull = {false};
        int window = 2;
        byte[] retained = new byte[0];
        int progress = 0;
        int offset = 0;
        boolean started = false;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(window, message.length - progress);
            byte[] buffer = new byte[retained.length + take];
            System.arraycopy(retained, 0, buffer, 0, retained.length);
            System.arraycopy(message, progress, buffer, retained.length, take);
            progress += take;
            boolean last = progress >= message.length;
            int limit = buffer.length;
            UnsafeBufferEx view = new UnsafeBufferEx(buffer);
            if (started)
            {
                parser.resume(view, offset, limit, last);
            }
            else
            {
                parser.wrap(view, offset, limit, last);
                started = true;
            }

            boolean starved = false;
            while (parser.hasNext() && !starved)
            {
                ProtobufEvent event = parser.nextEvent();
                if (event == null)
                {
                    sawNull[0] = true;
                    starved = true;
                }
                else
                {
                    record(parser, events, pending, event);
                }
            }

            if (parser.hasNext())
            {
                int parsed = limit - parser.remaining();
                retained = Arrays.copyOfRange(buffer, parsed, limit);
            }
            else
            {
                done = true;
            }
        }

        assertTrue(sawNull[0]);
        // chunked across 2-byte windows, the events match the whole-buffer stream once value chunks are reassembled
        assertEquals(whole, events);
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
        case FIELD:
            events.add("F" + parser.field().number());
            break;
        case VALUE:
            // a value may arrive in chunks (deferredBytes > 0); reassemble before recording
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

    @Test
    public void shouldChunkLargeStringAcrossWindows()
    {
        String text = "the quick brown fox jumps over the lazy dog";
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(text.getBytes(UTF_8));
        });

        List<Integer> deferreds = new ArrayList<>();
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        driveWindows(Protobuf.parser(schema, "P"), message, 8, (parser, event) ->
        {
            if (event == ProtobufEvent.VALUE)
            {
                deferreds.add(parser.deferredBytes());
                appendValue(parser, value);
            }
        });

        assertEquals(text, value.toString(UTF_8));
        assertTrue(deferreds.size() > 1, "expected multiple chunks");
        assertEquals(0, (int) deferreds.get(deferreds.size() - 1));
        for (int i = 1; i < deferreds.size(); i++)
        {
            assertTrue(deferreds.get(i) < deferreds.get(i - 1), "deferredBytes must strictly decrease");
        }
    }

    @Test
    public void shouldChunkStringOnCodePointBoundaries()
    {
        // 1-byte ASCII, 2-byte (é), 3-byte (☃) and 4-byte (😀) code points
        String text = "aé☃b😀c☃dé😀f☃gh😀ité☃klm";
        byte[] message = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(text.getBytes(UTF_8));
        });

        ByteArrayOutputStream value = new ByteArrayOutputStream();
        List<byte[]> chunks = new ArrayList<>();
        driveWindows(Protobuf.parser(schema, "P"), message, 4, (parser, event) ->
        {
            if (event == ProtobufEvent.VALUE)
            {
                byte[] chunk = new byte[parser.segment().capacity()];
                parser.segment().getBytes(0, chunk);
                chunks.add(chunk);
                value.writeBytes(chunk);
            }
        });

        assertEquals(text, value.toString(UTF_8));
        assertTrue(chunks.size() > 1, "expected multiple chunks");
        for (byte[] chunk : chunks)
        {
            // a chunk split mid-code-point would not round-trip through UTF-8
            assertArrayEquals(chunk, new String(chunk, UTF_8).getBytes(UTF_8));
        }
    }

    @Test
    public void shouldChunkBytesAcrossWindows()
    {
        byte[] data = new byte[40];
        for (int i = 0; i < data.length; i++)
        {
            // continuation-like bytes prove BYTES is split at the raw edge, never UTF-8 trimmed
            data[i] = (byte) (0x80 + (i % 0x40));
        }
        byte[] message = wire(w ->
        {
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes(data);
        });

        List<Integer> deferreds = new ArrayList<>();
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        driveWindows(Protobuf.parser(schema, "P"), message, 7, (parser, event) ->
        {
            if (event == ProtobufEvent.VALUE)
            {
                deferreds.add(parser.deferredBytes());
                appendValue(parser, value);
            }
        });

        assertArrayEquals(data, value.toByteArray());
        assertTrue(deferreds.size() > 1, "expected multiple chunks");
        assertEquals(0, (int) deferreds.get(deferreds.size() - 1));
    }

    private static void appendValue(
        ProtobufParser parser,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[parser.segment().capacity()];
        parser.segment().getBytes(0, chunk);
        sink.writeBytes(chunk);
    }

    // drives the parser window-by-window the way a real caller does: on starvation it retains the
    // unconsumed tail (the bytes the parser did not parse) and re-presents it contiguous with the next window
    private static void driveWindows(
        ProtobufParser parser,
        byte[] message,
        int window,
        BiConsumer<ProtobufParser, ProtobufEvent> consumer)
    {
        byte[] retained = new byte[0];
        int progress = 0;
        int offset = 0;
        boolean started = false;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(window, message.length - progress);
            byte[] buffer = new byte[retained.length + take];
            System.arraycopy(retained, 0, buffer, 0, retained.length);
            System.arraycopy(message, progress, buffer, retained.length, take);
            progress += take;
            boolean last = progress >= message.length;
            int limit = buffer.length;
            UnsafeBufferEx view = new UnsafeBufferEx(buffer);
            if (started)
            {
                parser.resume(view, offset, limit, last);
            }
            else
            {
                parser.wrap(view, offset, limit, last);
                started = true;
            }

            boolean starved = false;
            while (parser.hasNext() && !starved)
            {
                ProtobufEvent event = parser.nextEvent();
                if (event == null)
                {
                    starved = true;
                }
                else
                {
                    consumer.accept(parser, event);
                }
            }

            if (parser.hasNext())
            {
                int parsed = limit - parser.remaining();
                retained = Arrays.copyOfRange(buffer, parsed, limit);
            }
            else
            {
                done = true;
            }
        }
    }

    private static int count(
        ProtobufParser parser,
        byte[] message)
    {
        parser.wrap(new UnsafeBufferEx(message), 0, message.length);
        int events = 0;
        while (parser.hasNext())
        {
            parser.nextEvent();
            events++;
        }
        return events;
    }

    private static String scalar(
        ProtobufParser parser)
    {
        ProtobufField field = parser.field();
        String value;
        switch (field.type())
        {
        case STRING:
            byte[] text = new byte[parser.segment().capacity()];
            parser.segment().getBytes(0, text);
            value = new String(text, UTF_8);
            break;
        default:
            value = Long.toString(parser.longValue());
            break;
        }
        return value;
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
            .message(ProtobufMessage.builder("Addr")
                .field(ProtobufField.builder().number(1).name("city").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("P")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(4).name("home").type(ProtobufType.MESSAGE).typeName("Addr").build())
                .field(ProtobufField.builder().number(5).name("data").type(ProtobufType.BYTES).build())
                .build())
            .build();
    }
}
