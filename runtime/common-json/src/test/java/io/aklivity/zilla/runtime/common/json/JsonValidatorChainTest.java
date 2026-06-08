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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.json.stream.JsonParser;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEventConsumer.Status;

class JsonValidatorChainTest
{
    private static final String OBJECT_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}," +
        "\"name\":{\"type\":\"string\"}},\"required\":[\"id\",\"name\"]}";

    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);

    @Test
    void shouldForwardFullStreamWhenValid()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = StreamingJson.createGenerator().wrap(buffer, 0);
        JsonEventConsumer chain = schema.validator(JsonEventConsumer.of(gen));

        Status status = chain.drive(parserFor("{\"id\":1,\"name\":\"x\"} "));

        assertEquals(Status.COMPLETE, status);
        assertEquals("{\"id\":1,\"name\":\"x\"}", output(gen));
    }

    @Test
    void shouldValidateFullStreamWhileProjectingSubset()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = StreamingJson.createGenerator().wrap(buffer, 0);
        JsonEventConsumer chain = schema.validator(
            StreamingJson.createProjector(List.of("/id"), JsonEventConsumer.of(gen)));

        Status status = chain.drive(parserFor("{\"id\":1,\"name\":\"x\"} "));

        assertEquals(Status.COMPLETE, status);
        assertEquals("{\"id\":1}", output(gen));
    }

    @Test
    void shouldRejectMissingRequiredAfterEmitting()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = StreamingJson.createGenerator().wrap(buffer, 0);
        JsonEventConsumer chain = schema.validator(
            StreamingJson.createProjector(List.of("/id"), JsonEventConsumer.of(gen)));

        Status status = chain.drive(parserFor("{\"id\":1} "));

        assertEquals(Status.REJECTED, status);
        assertEquals("{\"id\":1}", output(gen));
    }

    @Test
    void shouldRejectTypeViolation()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = StreamingJson.createGenerator().wrap(buffer, 0);
        JsonEventConsumer chain = schema.validator(JsonEventConsumer.of(gen));

        Status status = chain.drive(parserFor("{\"id\":\"x\",\"name\":\"y\"} "));

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldResumeMidValueWithoutReset()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = StreamingJson.createGenerator().wrap(buffer, 0);
        JsonEventConsumer chain = schema.validator(JsonEventConsumer.of(gen));
        JsonParser parser = parserFor("{\"id\":1,\"name\":\"x\"} ");

        chain.reset();
        assertEquals(Status.PENDING, chain.feed(parser.next(), parser));
        assertEquals(Status.PENDING, chain.feed(parser.next(), parser));

        Status status = chain.pump(parser);

        assertEquals(Status.COMPLETE, status);
        assertEquals("{\"id\":1,\"name\":\"x\"}", output(gen));
    }

    @Test
    void shouldReuseChainAcrossValues()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = StreamingJson.createGenerator();
        JsonEventConsumer chain = schema.validator(JsonEventConsumer.of(gen));

        gen.wrap(buffer, 0);
        assertEquals(Status.COMPLETE, chain.drive(parserFor("{\"id\":1,\"name\":\"a\"} ")));
        assertEquals("{\"id\":1,\"name\":\"a\"}", output(gen));

        gen.wrap(buffer, 0);
        assertEquals(Status.REJECTED, chain.drive(parserFor("{\"id\":2} ")));
    }

    private String output(
        JsonGeneratorEx gen)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
