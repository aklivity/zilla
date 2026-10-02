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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.agrona.DirectBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmAnthropicEncodeSink;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmOpenaiDecodeTransform;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmOpenaiEncodeSink;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public class LlmLegacyOpenaiDialectTest
{
    private final Map<String, LlmLegacyDialectFactorySpi> factoriesByName = ServiceLoader
        .load(LlmLegacyDialectFactorySpi.class)
        .stream()
        .map(Supplier::get)
        .collect(toMap(LlmLegacyDialectFactorySpi::name, identity()));

    @Test
    public void shouldResolveRegisteredDialect()
    {
        assertThat(factoriesByName.keySet(), hasItem("openai"));
    }

    @Test
    public void shouldCreateMatchingDialect()
    {
        LlmLegacyDialect dialect = factoriesByName.get("openai").create(mock(LlmLegacyDialectContext.class));

        assertThat(dialect, not(nullValue()));
        assertThat(dialect.name(), equalTo("openai"));
    }

    @Test
    public void shouldResolveRequestPathUnderConfiguredBasePath()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.requestPath("/v1"), equalTo("/v1/chat/completions"));
        assertThat(dialect.requestPath("/aicomp/v1"), equalTo("/aicomp/v1/chat/completions"));
    }

    @Test
    public void shouldDetectChatCompletionsPost()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.detect(headers("POST", "/v1/chat/completions")), is(true));
    }

    @Test
    public void shouldDetectCompletionsPost()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.detect(headers("POST", "/v1/completions")), is(true));
    }

    @Test
    public void shouldDetectRegardlessOfMethodCase()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.detect(headers("post", "/v1/chat/completions")), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedPath()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.detect(headers("POST", "/v1/embeddings")), is(false));
    }

    @Test
    public void shouldNotDetectNonPostMethod()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.detect(headers("GET", "/v1/chat/completions")), is(false));
    }

    @Test
    public void shouldNotDetectMismatchedContentType()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/chat/completions"));
        envelope.set("content-type", value("application/vnd.zilla.test-unknown+json"));

        assertThat(dialect.detect(envelope), is(false));
    }

    @Test
    public void shouldNotDetectMissingContentType()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/chat/completions"));

        assertThat(dialect.detect(envelope), is(false));
    }

    @Test
    public void shouldNotDetectWithEmptyHeaders()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.detect(JsonEnvelope.NONE), is(false));
    }

    @Test
    public void shouldSupplyRequestDecoderAndEncoder()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.supplyRequestDecoder(JsonEnvelope.NONE), not(nullValue()));
        assertThat(dialect.supplyRequestEncoder(JsonEnvelope.NONE), not(nullValue()));
        assertThat(dialect.supplyRequestDecoder(JsonEnvelope.NONE).identity(), is(false));
        assertThat(dialect.supplyRequestEncoder(JsonEnvelope.NONE).identity(), is(false));
    }

    @Test
    public void shouldSupplyExtractorOnlyForRequestKind()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.supplyExtractor(LlmLegacyDialect.Kind.REQUEST, JsonEnvelope.NONE), not(nullValue()));
        assertThat(dialect.supplyExtractor(LlmLegacyDialect.Kind.REQUEST, JsonEnvelope.NONE).identity(), is(true));
        assertThat(dialect.supplyExtractor(LlmLegacyDialect.Kind.RESPONSE, JsonEnvelope.NONE).identity(), is(true));
    }

    @Test
    public void shouldDeclareContentTypes()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.requestContentType(), equalTo("application/json"));
        assertThat(dialect.responseContentTypes(), containsInAnyOrder("application/json", "text/event-stream"));
    }

    @Test
    public void shouldSupplyResponseExtractorReceivingEvents()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.supplyExtractor(LlmLegacyDialect.Kind.RESPONSE, JsonEnvelope.NONE),
            instanceOf(LlmLegacyDialectEvent.class));
    }

    @Test
    public void shouldEncodeRateLimitErrorBody()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.errorBody(429, "rate_limit_error", "Too many requests"), equalTo(
            "{\"error\":{\"message\":\"Too many requests\",\"type\":\"requests\",\"param\":null," +
                "\"code\":\"rate_limit_exceeded\"}}"));
    }

    @Test
    public void shouldEncodeServerErrorBodyWithDefaultMessage()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.errorBody(502, null, null), equalTo(
            "{\"error\":{\"message\":\"Bad Gateway\",\"type\":\"server_error\",\"param\":null,\"code\":null}}"));
    }

    @Test
    public void shouldEncodeClientErrorBodyEscapingMessage()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.errorBody(400, null, "bad \"field\""), equalTo(
            "{\"error\":{\"message\":\"bad \\\"field\\\"\",\"type\":\"invalid_request_error\",\"param\":null," +
                "\"code\":null}}"));
    }

    @Test
    public void shouldSupplySchemaValidatorForBothKinds()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        assertThat(dialect.supplySchemaValidator(LlmLegacyDialect.Kind.REQUEST), not(nullValue()));
        assertThat(dialect.supplySchemaValidator(LlmLegacyDialect.Kind.RESPONSE), not(nullValue()));
    }

    @Test
    public void shouldSupplyResponseDecodeTransform()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();

        JsonTransform transform = dialect.supplyResponseDecodeTransform();

        assertThat(transform, instanceOf(LlmOpenaiDecodeTransform.class));
    }

    @Test
    public void shouldSupplyResponseEncodeSink()
    {
        LlmLegacyDialect dialect = new LlmLegacyOpenaiDialect();
        List<String> discarded = new ArrayList<>();

        JsonSink sink = dialect.supplyResponseEncodeSink(JsonEnvelope.NONE,
            (name, buffer, offset, length) -> discarded.add(name));

        assertThat(sink, instanceOf(LlmOpenaiEncodeSink.class));
    }

    @Test
    public void shouldRoundTripResponseDecodeIntoAnotherDialectsResponseEncode()
    {
        LlmLegacyDialect openai = new LlmLegacyOpenaiDialect();
        LlmLegacyDialect anthropic = new LlmLegacyAnthropicDialect();
        List<Event> events = new ArrayList<>();

        JsonTransform decode = openai.supplyResponseDecodeTransform();
        JsonSink encode = anthropic.supplyResponseEncodeSink(JsonEnvelope.NONE, (name, buffer, offset, length) ->
            events.add(new Event(name, readBody(buffer, offset, length))));

        assertThat(decode, instanceOf(LlmOpenaiDecodeTransform.class));
        assertThat(encode, instanceOf(LlmAnthropicEncodeSink.class));

        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).transform(decode).into(encode);

        String json = "{\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\"," +
            "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}";
        byte[] bytes = json.getBytes(UTF_8);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertThat(status, equalTo(Status.COMPLETED));
        assertThat(events.size(), equalTo(2));
        assertThat(events.get(0).name, equalTo("message_start"));
        assertThat(events.get(0).body.getJsonObject("message").getString("id"), equalTo("chatcmpl-1"));
        assertThat(events.get(1).name, equalTo("content_block_start"));
    }

    private static JsonObject readBody(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        String text = buffer.getStringWithoutLengthUtf8(offset, length);
        try (JsonReader reader = Json.createReader(new StringReader(text)))
        {
            return reader.readObject();
        }
    }

    private static final class Event
    {
        private final String name;
        private final JsonObject body;

        private Event(
            String name,
            JsonObject body)
        {
            this.name = name;
            this.body = body;
        }
    }

    private static JsonEnvelope headers(
        String method,
        String path)
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value(method));
        envelope.set(":path", value(path));
        envelope.set("content-type", value("application/json"));
        return envelope;
    }

    private static DirectBufferEx value(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
