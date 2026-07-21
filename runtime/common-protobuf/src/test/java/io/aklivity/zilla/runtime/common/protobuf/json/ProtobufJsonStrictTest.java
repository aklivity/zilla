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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;

public class ProtobufJsonStrictTest
{
    private static final String SCHEMA =
        "syntax = \"proto3\";\n" +
        "message Person { string name = 1; int32 id = 2; }\n";

    // captures the call-scoped diagnostic the pipeline pushes on a terminal REJECTED, copying the message out
    private final String[] reason = new String[1];
    private final ProtobufReporter reporter = d -> reason[0] = d.message();

    @Test
    public void shouldRejectUnknownFieldWhenConfigured()
    {
        ProtobufSchema schema = Protobuf.schema(SCHEMA);
        ProtobufGenerator generator = Protobuf.generator().wrap(new UnsafeBufferEx(new byte[256]), 0, 256);
        ProtobufParser parser = ProtobufJson.parser(JsonEx.createParser(), schema, "Person",
            Map.of(ProtobufJson.REJECT_UNKNOWN_FIELDS, Boolean.TRUE));
        ProtobufPipeline pipeline = Protobuf.stream(parser).reporting(reporter).into(ProtobufSink.of(generator, schema,
            "Person"));
        pipeline.reset();

        byte[] in = "{\"name\":\"neo\",\"nope\":1}".getBytes(UTF_8);
        assertEquals(Status.REJECTED, pipeline.transform(new UnsafeBufferEx(in), 0, in.length));
        assertTrue(reason[0].contains("nope"), reason[0]);
    }

    @Test
    public void shouldIgnoreUnknownFieldByDefault()
    {
        ProtobufSchema schema = Protobuf.schema(SCHEMA);
        ProtobufGenerator generator = Protobuf.generator().wrap(new UnsafeBufferEx(new byte[256]), 0, 256);
        ProtobufParser parser = ProtobufJson.parser(JsonEx.createParser(), schema, "Person");
        ProtobufPipeline pipeline = Protobuf.stream(parser).reporting(reporter).into(ProtobufSink.of(generator, schema,
            "Person"));
        pipeline.reset();

        byte[] in = "{\"name\":\"neo\",\"nope\":1}".getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(in), 0, in.length));
        assertNull(reason[0]);
    }

    @Test
    public void shouldNotReportOnSuccess()
    {
        ProtobufSchema schema = Protobuf.schema(SCHEMA);
        ProtobufGenerator generator = Protobuf.generator().wrap(new UnsafeBufferEx(new byte[256]), 0, 256);
        ProtobufParser parser = ProtobufJson.parser(JsonEx.createParser(), schema, "Person",
            Map.of(ProtobufJson.REJECT_UNKNOWN_FIELDS, Boolean.TRUE));
        ProtobufPipeline pipeline = Protobuf.stream(parser).reporting(reporter).into(ProtobufSink.of(generator, schema,
            "Person"));
        pipeline.reset();

        byte[] in = "{\"name\":\"neo\"}".getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(in), 0, in.length));
        assertNull(reason[0]);
    }

    @Test
    public void shouldNotReportOnSuccessAfterReject()
    {
        ProtobufSchema schema = Protobuf.schema(SCHEMA);
        ProtobufGenerator generator = Protobuf.generator();
        ProtobufParser parser = ProtobufJson.parser(JsonEx.createParser(), schema, "Person",
            Map.of(ProtobufJson.REJECT_UNKNOWN_FIELDS, Boolean.TRUE));
        ProtobufPipeline pipeline = Protobuf.stream(parser).reporting(reporter).into(ProtobufSink.of(generator, schema,
            "Person"));

        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[256]);
        generator.wrap(out, 0, out.capacity());
        pipeline.reset();
        byte[] bad = "{\"nope\":1}".getBytes(UTF_8);
        assertEquals(Status.REJECTED, pipeline.transform(new UnsafeBufferEx(bad), 0, bad.length));

        generator.wrap(out, 0, out.capacity());
        pipeline.reset();
        // the prior reject's message must not leak onto a clean value: the reporter fires only on REJECTED
        reason[0] = null;
        byte[] good = "{\"name\":\"neo\"}".getBytes(UTF_8);
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(good), 0, good.length));
        assertNull(reason[0]);
    }
}
