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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Drives a wire-to-wire pipeline through a bounded input window and hands each call's output straight to the
 * consumer, as a bounded caller does. A nested message larger than the window starves mid-message, so the output
 * of that call must already be a valid prefix of the stream: the concatenated output decodes to the same message.
 */
public class ProtobufPipelineWindowTest
{
    private static final int[] SLOTS = {16, 24, 32, 40};

    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldForwardNestedMessageLargerThanWindow()
    {
        byte[] message = person(address("x".repeat(40), "Zion"));

        for (int slot : SLOTS)
        {
            assertEquals(flatten(message), flatten(forward(message, slot)), "window " + slot);
        }
    }

    @Test
    public void shouldForwardPackedBlockLargerThanWindow()
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

        for (int slot : SLOTS)
        {
            assertEquals(flatten(message), flatten(forward(message, slot)), "window " + slot);
        }
    }

    private byte[] forward(
        byte[] message,
        int slot)
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "Person"))
            .into(Protobuf.generator(), schema, "Person");
        pipeline.reset();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[4096]);
        ByteArrayOutputStream forwarded = new ByteArrayOutputStream();
        byte[] retained = new byte[0];
        int progress = 0;
        boolean done = false;
        while (!done)
        {
            int take = Math.min(slot - retained.length, message.length - progress);
            byte[] buffer = new byte[retained.length + take];
            System.arraycopy(retained, 0, buffer, 0, retained.length);
            System.arraycopy(message, progress, buffer, retained.length, take);
            progress += take;
            boolean last = progress >= message.length;
            ProtobufPipelineResult result = pipeline.transform(new UnsafeBufferEx(buffer), 0, buffer.length, last,
                out, 0, out.capacity());
            assertTrue(result.consumed() > 0 || buffer.length < slot || last, "stalled at window " + slot);
            byte[] produced = new byte[result.produced()];
            out.getBytes(0, produced);
            forwarded.writeBytes(produced);
            retained = Arrays.copyOfRange(buffer, result.consumed(), buffer.length);
            done = result.status() == ProtobufPipeline.Status.COMPLETED;
        }
        return forwarded.toByteArray();
    }

    // the scalar field and value events only, since a nested message written in more than one record decodes, by
    // merge, to the same fields
    private List<String> flatten(
        byte[] message)
    {
        ProtobufParser parser = Protobuf.parser(schema, "Person").wrap(new UnsafeBufferEx(message), 0, message.length);
        List<String> events = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        while (parser.hasNext())
        {
            ProtobufEvent event = parser.nextEvent();
            if (event == ProtobufEvent.FIELD && !parser.field().composite())
            {
                events.add("F" + parser.field().number());
            }
            else if (event == ProtobufEvent.VALUE)
            {
                pending.append(parser.field().type() == ProtobufType.STRING
                    ? string(parser)
                    : Long.toString(parser.longValue()));
                if (parser.deferredBytes() == 0)
                {
                    events.add("V" + pending);
                    pending.setLength(0);
                }
            }
        }
        return events;
    }

    private static String string(
        ProtobufParser parser)
    {
        byte[] text = new byte[parser.segment().capacity()];
        parser.segment().getBytes(0, text);
        return new String(text, UTF_8);
    }

    private static byte[] person(
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

    private static byte[] address(
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
            .build();
    }
}
