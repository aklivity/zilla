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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

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
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonSink.of(gen));

        Status status = run(pipeline, "{\"id\":1,\"name\":\"x\"} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1,\"name\":\"x\"}", output(gen));
    }

    @Test
    void shouldValidateFullStreamWhileProjectingSubset()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(JsonEx.projector(List.of("/id")))
            .into(JsonSink.of(gen));

        Status status = run(pipeline, "{\"id\":1,\"name\":\"x\"} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1}", output(gen));
    }

    @Test
    void shouldRejectMissingRequiredAfterEmitting()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(JsonEx.projector(List.of("/id")))
            .into(JsonSink.of(gen));

        Status status = run(pipeline, "{\"id\":1} ");

        assertEquals(Status.REJECTED, status);
        assertEquals("{\"id\":1}", output(gen));
    }

    @Test
    void shouldRejectTypeViolation()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonSink.of(gen));

        Status status = run(pipeline, "{\"id\":\"x\",\"name\":\"y\"} ");

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldResumeMidValueWithoutReset()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonSink.of(gen));

        byte[] bytes = "{\"id\":1,\"name\":\"x\"} ".getBytes(UTF_8);
        UnsafeBuffer in = new UnsafeBuffer(bytes);

        pipeline.reset();
        assertEquals(Status.ADVANCED, pipeline.feed(in, 0, 8));
        Status status = pipeline.feed(in, 8, bytes.length - 8);

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1,\"name\":\"x\"}", output(gen));
    }

    @Test
    void shouldReuseChainAcrossValues()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonGeneratorEx gen = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .into(JsonSink.of(gen));

        gen.wrap(buffer, 0, buffer.capacity());
        assertEquals(Status.COMPLETED, run(pipeline, "{\"id\":1,\"name\":\"a\"} "));
        assertEquals("{\"id\":1,\"name\":\"a\"}", output(gen));

        gen.wrap(buffer, 0, buffer.capacity());
        assertEquals(Status.REJECTED, run(pipeline, "{\"id\":2} "));
    }

    @Test
    void shouldForwardThroughDefaultResetTransform()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonTransform passthrough = (control, source, event, sink) -> sink.feed(control, source, event);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(passthrough)
            .into(JsonSink.of(gen));

        Status status = run(pipeline, "{\"id\":1} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"id\":1}", output(gen));
    }

    @Test
    void shouldPullValidDocumentThroughNewParser()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonParser parser = schema.newParser(true, parserFor("{\"id\":1,\"name\":\"x\"} "));

        int events = 0;
        while (parser.hasNext())
        {
            parser.next();
            events++;
        }

        assertEquals(6, events);
    }

    @Test
    void shouldThrowFromNewParserOnInvalid()
    {
        JsonSchema schema = JsonSchema.of(OBJECT_SCHEMA);
        JsonParser parser = schema.newParser(true, parserFor("{\"id\":\"x\",\"name\":\"y\"} "));

        assertThrows(JsonValidationException.class, () ->
        {
            while (parser.hasNext())
            {
                parser.next();
            }
        });
    }

    @Test
    void shouldValidateValueFromNewParser()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"integer\",\"minimum\":0}");
        JsonParser parser = schema.newParser(true,
            JsonEx.createParserFactory(Map.of()).createParser(streamFor("7 ")));

        Event event = parser.next();

        assertEquals(Event.VALUE_NUMBER, event);
        assertEquals(7, parser.getInt());
    }

    private String output(
        JsonGeneratorEx gen)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static Status run(
        JsonPipeline pipeline,
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        pipeline.reset();
        return pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);
    }

    private static JsonParser parserFor(
        String text)
    {
        return JsonEx.createParser(streamFor(text));
    }

    private static InputStream streamFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return in;
    }
}
