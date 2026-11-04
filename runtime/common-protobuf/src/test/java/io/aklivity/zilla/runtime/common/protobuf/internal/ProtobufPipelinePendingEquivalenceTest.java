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
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

// a stage that returns PENDING must neither split nor duplicate a message element: the output of a run that
// suspends at every string is byte-for-byte the output of the same run that never suspends
class ProtobufPipelinePendingEquivalenceTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    void shouldHoldNestedSingularMessagesOpenAcrossPending()
    {
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(wire(middle ->
            {
                middle.writeTag(1, ProtobufWireType.LEN);
                middle.writeBytes(wire(inner ->
                {
                    inner.writeTag(1, ProtobufWireType.LEN);
                    inner.writeBytes("value".getBytes(UTF_8));
                }));
            }));
        });

        assertEquivalent("Outer", input, input.length, 256, true, false);
    }

    @Test
    void shouldNotSplitRepeatedMessageElementsAcrossPending()
    {
        byte[] input = order(item("pen", "blue"), item("ink", "black"));

        assertEquivalent("Order", input, input.length, 256, true, false);
    }

    @Test
    void shouldPendInsideEachElementOfRepeatedMessageField()
    {
        byte[] input = order(item("a", "b"), item("c", "d"), item("e", "f"));

        assertEquivalent("Order", input, input.length, 256, true, false);
    }

    @Test
    void shouldPendInTheMiddleOfLengthDelimitedValue()
    {
        byte[] input = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("v".repeat(200).getBytes(UTF_8));
        });

        assertEquivalent("Inner", input, 24, 1024, true, true);
    }

    private void assertEquivalent(
        String message,
        byte[] input,
        int windowSize,
        int dstCap,
        boolean pendOnValue,
        boolean pendOnChunk)
    {
        byte[] expected = drive(new Waiting(false, false), message, input, windowSize, dstCap);
        Waiting waiting = new Waiting(pendOnValue, pendOnChunk);

        byte[] actual = drive(waiting, message, input, windowSize, dstCap);

        assertTrue(waiting.pended > 0, "expected the transform to suspend");
        assertArrayEquals(expected, actual);
    }

    private byte[] drive(
        Waiting transform,
        String message,
        byte[] input,
        int windowSize,
        int dstCap)
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, message))
            .transform(transform)
            .into(Protobuf.generator(), schema, message);
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

    // suspends once at each string value (and, when asked, at each chunk of a streaming value) before forwarding it
    private static final class Waiting implements ProtobufTransform
    {
        private final boolean pendOnValue;
        private final boolean pendOnChunk;

        private int pended;
        private Runnable completion;
        private boolean released;

        private Waiting(
            boolean pendOnValue,
            boolean pendOnChunk)
        {
            this.pendOnValue = pendOnValue;
            this.pendOnChunk = pendOnChunk;
        }

        void complete()
        {
            released = true;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        @Override
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            Status status;
            boolean pendable = event == ProtobufEvent.VALUE &&
                (source.deferredBytes() == 0 ? pendOnValue : pendOnChunk);
            if (pendable && !released)
            {
                pended++;
                completion = control.resumer();
                status = Status.PENDING;
            }
            else
            {
                released = false;
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }

    private static byte[] item(
        String name,
        String note)
    {
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(note.getBytes(UTF_8));
        });
    }

    private static byte[] order(
        byte[]... items)
    {
        return wire(w ->
        {
            for (byte[] item : items)
            {
                w.writeTag(1, ProtobufWireType.LEN);
                w.writeBytes(item);
            }
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
            .message(ProtobufMessage.builder("Inner")
                .field(ProtobufField.builder().number(1).name("v").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Middle")
                .field(ProtobufField.builder().number(1).name("inner").type(ProtobufType.MESSAGE).typeName("Inner").build())
                .build())
            .message(ProtobufMessage.builder("Outer")
                .field(ProtobufField.builder().number(1).name("middle").type(ProtobufType.MESSAGE).typeName("Middle").build())
                .build())
            .message(ProtobufMessage.builder("Item")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("note").type(ProtobufType.STRING).build())
                .build())
            .message(ProtobufMessage.builder("Order")
                .field(ProtobufField.builder().number(1).name("items").type(ProtobufType.MESSAGE).typeName("Item")
                    .repeated(true).build())
                .build())
            .build();
    }
}
