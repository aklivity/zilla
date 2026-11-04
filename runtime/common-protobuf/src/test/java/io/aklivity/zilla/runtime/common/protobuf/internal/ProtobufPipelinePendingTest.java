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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

class ProtobufPipelinePendingTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    void shouldResumePendingScalarWithoutLossOrDuplication()
    {
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
        });
        PendingTransform pending = new PendingTransform(1, 0);

        byte[] output = drive(pending, input, input.length, 64);

        assertDecoded(output, "neo", null, null);
        assertEquals(1, pending.pended);
        assertEquals("neo", pending.captured);
    }

    @Test
    void shouldResumePendingWhileLengthDelimitedValueIsCapturedAcrossWindows()
    {
        String street = "x".repeat(200);
        byte[] input = person("neo", street, "Zion");
        PendingTransform pending = new PendingTransform(1, 1);

        byte[] output = drive(pending, input, 24, 1024);

        assertDecoded(output, "neo", street, "Zion");
        assertEquals(1, pending.pended);
        assertEquals(street, pending.captured);
    }

    @Test
    void shouldResumePendingInsideNestedMessage()
    {
        byte[] input = person("neo", "matrix", "Zion");
        PendingTransform pending = new PendingTransform(2, 0);

        byte[] output = drive(pending, input, input.length, 1024);

        assertDecoded(output, "neo", "matrix", "Zion");
        assertEquals(1, pending.pended);
        assertEquals("Zion", pending.captured);
    }

    @Test
    void shouldOverflowOutputAfterResumption()
    {
        String street = "y".repeat(300);
        byte[] input = person("neo", street, "Zion");
        PendingTransform pending = new PendingTransform(1, 1);

        byte[] output = drive(pending, input, input.length, 32);

        assertDecoded(output, "neo", street, "Zion");
        assertEquals(1, pending.pended);
        assertEquals(street, pending.captured);
    }

    @Test
    void shouldNotChangeBehaviorForTransformThatNeverWaits()
    {
        String street = "y".repeat(300);
        byte[] input = person("neo", street, "Zion");
        PendingTransform never = new PendingTransform(1, 99);

        byte[] output = drive(never, input, 20, 64);

        assertDecoded(output, "neo", street, "Zion");
        assertEquals(0, never.pended);
    }

    private void assertDecoded(
        byte[] message,
        String name,
        String street,
        String city)
    {
        ProtobufParser parser = Protobuf.parser(schema, "Person").wrap(new UnsafeBufferEx(message), 0, message.length);
        String[] decoded = new String[3];
        ProtobufField current = null;
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
                current = parser.field();
                break;
            case VALUE:
                byte[] value = new byte[parser.segment().capacity()];
                parser.segment().getBytes(0, value);
                int slot = depth == 1 ? 0 : current.number();
                decoded[slot] = decoded[slot] == null ? new String(value, UTF_8) : decoded[slot] + new String(value, UTF_8);
                break;
            default:
                break;
            }
        }
        assertEquals(name, decoded[0]);
        assertEquals(street, decoded[1]);
        assertEquals(city, decoded[2]);
    }

    private byte[] drive(
        PendingTransform transform,
        byte[] input,
        int windowSize,
        int dstCap)
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "Person"))
            .transform(transform)
            .into(Protobuf.generator(), schema, "Person");
        pipeline.reset();
        pipeline.resumer(transform::complete);

        MutableDirectBufferEx src = new UnsafeBufferEx(input);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[dstCap]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int position = 0;
        int end = Math.min(windowSize, input.length);
        Status status;
        int guard = 0;
        do
        {
            ProtobufPipelineResult result = pipeline.transform(src, position, end, end == input.length, dst, 0, dstCap);
            status = result.status();
            byte[] chunk = new byte[result.produced()];
            dst.getBytes(0, chunk);
            output.writeBytes(chunk);
            position += result.consumed();
            if (status == Status.STARVED)
            {
                end = Math.min(end + windowSize, input.length);
            }
            else if (status == Status.PENDING)
            {
                assertNotNull(transform.completion, "pending without asynchronous work started");
                transform.completion.run();
            }
            assertTrue(++guard < 10_000);
        }
        while (status != Status.COMPLETED);

        return output.toByteArray();
    }

    private static final class PendingTransform implements ProtobufTransform
    {
        private final int fieldNumber;
        private final int occurrence;

        private int seen;
        private int pended;
        private String captured;
        private String lastChunk;
        private Runnable completion;
        private boolean completed;
        private final ByteArrayOutputStream value = new ByteArrayOutputStream();

        private PendingTransform(
            int fieldNumber,
            int occurrence)
        {
            this.fieldNumber = fieldNumber;
            this.occurrence = occurrence;
        }

        @Override
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            Status status;
            if (event == ProtobufEvent.VALUE && source.fieldNumber() == fieldNumber && source.deferredBytes() == 0)
            {
                if (seen == occurrence && !completed)
                {
                    capture(source);
                    pended++;
                    completion = control.resumer();
                    status = Status.PENDING;
                }
                else
                {
                    if (seen == occurrence)
                    {
                        assertEquals(lastChunk, new String(bytes(source), UTF_8));
                        completed = false;
                    }
                    seen++;
                    status = sink.transform(control, source, event);
                }
            }
            else if (event == ProtobufEvent.VALUE && source.fieldNumber() == fieldNumber)
            {
                value.writeBytes(bytes(source));
                status = sink.transform(control, source, event);
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }

        private void capture(
            ProtobufSource source)
        {
            byte[] chunk = bytes(source);
            lastChunk = new String(chunk, UTF_8);
            value.writeBytes(chunk);
            captured = value.toString(UTF_8);
            value.reset();
        }

        void complete()
        {
            completed = true;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    private static byte[] bytes(
        ProtobufSource source)
    {
        byte[] bytes = new byte[source.segment().capacity()];
        source.segment().getBytes(0, bytes);
        return bytes;
    }

    private static byte[] person(
        String name,
        String street,
        String city)
    {
        byte[] home = wire(w ->
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
            w.writeBytes(home);
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
                .field(ProtobufField.builder().number(2).name("home").type(ProtobufType.MESSAGE).typeName("Address").build())
                .build())
            .build();
    }
}
