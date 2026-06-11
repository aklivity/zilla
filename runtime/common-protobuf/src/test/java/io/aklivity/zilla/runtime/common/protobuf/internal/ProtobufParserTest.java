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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

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

        ProtobufParser parser = Protobuf.parser(schema, "P").wrap(new UnsafeBuffer(message), 0, message.length);

        List<String> events = new ArrayList<>();
        int rootLength = -1;
        int nestedLength = -1;
        String rootName = null;
        String nestedName = null;
        int depth = 0;
        while (parser.hasNextEvent())
        {
            ProtobufEvent event = parser.nextEvent();
            switch (event)
            {
            case START_MESSAGE:
                events.add("{");
                if (depth == 0)
                {
                    rootLength = parser.length();
                    rootName = parser.message().name();
                }
                else
                {
                    nestedLength = parser.length();
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

        ProtobufParser parser = Protobuf.parser(broken, "P").wrap(new UnsafeBuffer(message), 0, message.length);
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

        ProtobufParser parser = Protobuf.parser().wrap(new UnsafeBuffer(message), 0, message.length);

        List<String> events = new ArrayList<>();
        while (parser.hasNextEvent())
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
                    ? "L" + parser.length()
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
        ProtobufParser parser = Protobuf.parser(schema, "Nope").wrap(new UnsafeBuffer(new byte[0]), 0, 0);

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

    private static int count(
        ProtobufParser parser,
        byte[] message)
    {
        parser.wrap(new UnsafeBuffer(message), 0, message.length);
        int events = 0;
        while (parser.hasNextEvent())
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
            byte[] text = new byte[parser.length()];
            parser.buffer().getBytes(parser.offset(), text);
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
            .message(ProtobufMessage.builder("P")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(4).name("home").type(ProtobufType.MESSAGE).typeName("Addr").build())
                .build())
            .build();
    }
}
