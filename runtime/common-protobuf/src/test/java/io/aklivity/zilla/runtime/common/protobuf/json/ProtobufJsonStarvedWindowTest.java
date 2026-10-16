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
package io.aklivity.zilla.runtime.common.protobuf.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;

class ProtobufJsonStarvedWindowTest
{
    private static final String MESSAGE = "test.Echo";

    private final ProtobufSchema schema = Protobuf.schema(
        "syntax = \"proto3\";\n" +
        "package test;\n" +
        "message Echo { string text = 1; int32 count = 2; }\n");

    @Test
    void shouldRenderStringSpanningInputWindowsAsOneDocument()
    {
        final String text = "abcdefghij".repeat(30);
        final byte[] wire = echo(text, 7);

        final String actual = drive(wire, 64);

        assertEquals("{\"text\":\"%s\",\"count\":7}".formatted(text), actual);
    }

    @Test
    void shouldRenderMessageSplitBetweenFieldsAsOneDocument()
    {
        final byte[] wire = echo("hello", 42);

        final String actual = drive(wire, 7);

        assertEquals("{\"text\":\"hello\",\"count\":42}", actual);
    }

    private String drive(
        byte[] wire,
        int windowSize)
    {
        final ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, MESSAGE))
            .into(ProtobufJson.generator(JsonEx.createGenerator(), schema, MESSAGE), schema, MESSAGE);
        final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[8192]);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        pipeline.reset();

        byte[] pending = new byte[0];
        int position = 0;
        Status status = Status.STARVED;
        while (status == Status.STARVED)
        {
            final int next = Math.min(windowSize, wire.length - position);
            final byte[] window = Arrays.copyOf(pending, pending.length + next);
            System.arraycopy(wire, position, window, pending.length, next);
            position += next;

            final ProtobufPipelineResult result = pipeline.transform(
                new UnsafeBufferEx(window), 0, window.length, position == wire.length, dst, 0, dst.capacity());

            final byte[] produced = new byte[result.produced()];
            dst.getBytes(0, produced);
            out.writeBytes(produced);

            status = result.status();
            pending = Arrays.copyOfRange(window, result.consumed(), window.length);
        }

        assertEquals(Status.COMPLETED, status);

        return out.toString(UTF_8);
    }

    private static byte[] echo(
        String text,
        int count)
    {
        final byte[] value = text.getBytes(UTF_8);
        final ByteArrayOutputStream wire = new ByteArrayOutputStream();

        wire.write(0x0a);
        for (int length = value.length; ; length >>>= 7)
        {
            if (length < 0x80)
            {
                wire.write(length);
                break;
            }
            wire.write((length & 0x7f) | 0x80);
        }
        wire.writeBytes(value);
        wire.write(0x10);
        wire.write(count);

        return wire.toByteArray();
    }
}
