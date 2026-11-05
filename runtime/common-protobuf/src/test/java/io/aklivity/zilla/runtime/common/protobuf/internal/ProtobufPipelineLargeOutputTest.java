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
import java.util.ArrayList;
import java.util.List;
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

// output that spans many destination windows, through nested messages, with and without a stage that suspends
class ProtobufPipelineLargeOutputTest
{
    private static final int VALUES = 300;

    private final ProtobufSchema schema = newSchema();

    @Test
    void shouldDrainLargeNestedOutputAcrossWindowsWithoutSuspension()
    {
        byte[] input = nested(VALUES);

        List<String> values = values(drive(new Waiting(0), input, input.length, 256));

        assertEquals(expected(VALUES), values);
    }

    @Test
    void shouldDrainLargeNestedOutputAcrossWindowsWhenInputIsWindowed()
    {
        byte[] input = nested(VALUES);

        List<String> values = values(drive(new Waiting(0), input, 100, 256));

        assertEquals(expected(VALUES), values);
    }

    @Test
    void shouldDrainLargeNestedOutputAcrossWindowsAfterSuspension()
    {
        byte[] input = nested(VALUES);

        List<String> values = values(drive(new Waiting(10), input, input.length, 256));

        assertEquals(expected(VALUES), values);
    }

    @Test
    void shouldDrainLargeNestedOutputAcrossWindowsAfterSuspensionWhenInputIsWindowed()
    {
        byte[] input = nested(VALUES);

        List<String> values = values(drive(new Waiting(10), input, 100, 256));

        assertEquals(expected(VALUES), values);
    }

    private byte[] drive(
        Waiting transform,
        byte[] input,
        int windowSize,
        int dstCap)
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "Outer"))
            .transform(transform)
            .into(Protobuf.generator(), schema, "Outer");
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
                assertNotNull(transform.completion);
                transform.completion.run();
            }
            assertTrue(++guard < 100_000, "pipeline did not complete");
        }
        while (status != Status.COMPLETED);

        return output.toByteArray();
    }

    private List<String> values(
        byte[] message)
    {
        ProtobufParser parser = Protobuf.parser(schema, "Outer").wrap(new UnsafeBufferEx(message), 0, message.length);
        List<String> values = new ArrayList<>();
        while (parser.hasNext())
        {
            if (parser.nextEvent() == ProtobufEvent.VALUE)
            {
                byte[] value = new byte[parser.segment().capacity()];
                parser.segment().getBytes(0, value);
                values.add(new String(value, UTF_8));
            }
        }
        return values;
    }

    private static List<String> expected(
        int count)
    {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            values.add(value(i));
        }
        return values;
    }

    private static String value(
        int index)
    {
        return "value-%04d".formatted(index);
    }

    // suspends once before forwarding every nth string value, 0 for never
    private static final class Waiting implements ProtobufTransform
    {
        private final int every;

        private int seen;
        private Runnable completion;
        private boolean released;

        private Waiting(
            int every)
        {
            this.every = every;
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
            if (event == ProtobufEvent.VALUE && every > 0 && source.deferredBytes() == 0 && seen % every == 0 && !released)
            {
                completion = control.resumer();
                status = Status.PENDING;
            }
            else
            {
                if (event == ProtobufEvent.VALUE && source.deferredBytes() == 0)
                {
                    seen++;
                }
                released = false;
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }

    private static byte[] nested(
        int count)
    {
        byte[] inner = wire(w ->
        {
            for (int i = 0; i < count; i++)
            {
                w.writeTag(1, ProtobufWireType.LEN);
                w.writeBytes(value(i).getBytes(UTF_8));
            }
        });
        byte[] middle = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(inner);
        });
        return wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(middle);
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
                .field(ProtobufField.builder().number(1).name("v").type(ProtobufType.STRING).repeated(true).build())
                .build())
            .message(ProtobufMessage.builder("Middle")
                .field(ProtobufField.builder().number(1).name("inner").type(ProtobufType.MESSAGE).typeName("Inner").build())
                .build())
            .message(ProtobufMessage.builder("Outer")
                .field(ProtobufField.builder().number(1).name("middle").type(ProtobufType.MESSAGE).typeName("Middle").build())
                .build())
            .build();
    }
}
