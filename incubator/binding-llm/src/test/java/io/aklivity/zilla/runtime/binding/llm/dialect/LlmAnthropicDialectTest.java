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

import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmAnthropicDecodeTransform;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmAnthropicEncodeSink;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmOpenaiEncodeSink;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public class LlmAnthropicDialectTest
{
    private final Map<String, LlmDialectFactorySpi> factoriesByName = ServiceLoader
        .load(LlmDialectFactorySpi.class)
        .stream()
        .map(Supplier::get)
        .collect(toMap(LlmDialectFactorySpi::name, identity()));

    @Test
    public void shouldResolveRegisteredDialect()
    {
        assertThat(factoriesByName.keySet(), hasItem("anthropic"));
    }

    @Test
    public void shouldCreateMatchingDialect()
    {
        LlmDialect dialect = factoriesByName.get("anthropic").create(mock(LlmDialectContext.class));

        assertThat(dialect, not(nullValue()));
        assertThat(dialect.name(), equalTo("anthropic"));
    }

    @Test
    public void shouldResolveRequestPathUnderConfiguredBasePath()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.requestPath("/v1"), equalTo("/v1/messages"));
        assertThat(dialect.requestPath("/aicomp/v1"), equalTo("/aicomp/v1/messages"));
    }

    @Test
    public void shouldDetectMessagesPostByPathAlone()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.detect(headers("POST", "/v1/messages")), is(true));
    }

    @Test
    public void shouldDetectRegardlessOfMethodCase()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.detect(headers("post", "/v1/messages")), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedPathWithNoOtherSignal()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.detect(headers("POST", "/v1/embeddings")), is(false));
    }

    @Test
    public void shouldNotDetectNonPostMethodWithNoOtherSignal()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.detect(headers("GET", "/v1/messages")), is(false));
    }

    @Test
    public void shouldDetectAnthropicVersionHeaderAlone()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/embeddings"));
        envelope.set("anthropic-version", value("2023-06-01"));

        assertThat(dialect.detect(envelope), is(true));
    }

    @Test
    public void shouldDetectApiKeyHeaderWithoutAuthorizationAlone()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/embeddings"));
        envelope.set("x-api-key", value("sk-ant-test"));

        assertThat(dialect.detect(envelope), is(true));
    }

    @Test
    public void shouldNotDetectApiKeyHeaderAlongsideAuthorization()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/embeddings"));
        envelope.set("x-api-key", value("sk-ant-test"));
        envelope.set("authorization", value("Bearer token"));

        assertThat(dialect.detect(envelope), is(false));
    }

    @Test
    public void shouldNotDetectWithEmptyHeaders()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.detect(JsonEnvelope.NONE), is(false));
    }

    @Test
    public void shouldSupplyDecodersAndEncodersForEachKind()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        for (LlmDialect.Kind kind : LlmDialect.Kind.values())
        {
            assertThat(dialect.supplyDecoder(kind, JsonEnvelope.NONE), not(nullValue()));
            assertThat(dialect.supplyEncoder(kind, JsonEnvelope.NONE), not(nullValue()));
        }

        assertThat(dialect.supplyDecoder(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE).identity(), is(false));
        assertThat(dialect.supplyEncoder(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE).identity(), is(false));
        assertThat(dialect.supplyDecoder(LlmDialect.Kind.RESPONSE, JsonEnvelope.NONE).identity(), is(true));
        assertThat(dialect.supplyEncoder(LlmDialect.Kind.RESPONSE, JsonEnvelope.NONE).identity(), is(true));
    }

    @Test
    public void shouldSupplyExtractorOnlyForRequestKind()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.supplyExtractor(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE), not(nullValue()));
        assertThat(dialect.supplyExtractor(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE).identity(), is(true));
        assertThat(dialect.supplyExtractor(LlmDialect.Kind.RESPONSE, JsonEnvelope.NONE).identity(), is(true));
    }

    @Test
    public void shouldSupplySchemaValidatorForBothKinds()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.supplySchemaValidator(LlmDialect.Kind.REQUEST), not(nullValue()));
        assertThat(dialect.supplySchemaValidator(LlmDialect.Kind.RESPONSE), not(nullValue()));
    }

    @Test
    public void shouldSupplyResponseDecodeTransform()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        JsonTransform transform = dialect.supplyResponseDecodeTransform();

        assertThat(transform, instanceOf(LlmAnthropicDecodeTransform.class));
    }

    @Test
    public void shouldSupplyResponseEncodeSink()
    {
        LlmDialect dialect = new LlmAnthropicDialect();
        List<String> discarded = new ArrayList<>();

        JsonSink sink = dialect.supplyResponseEncodeSink(JsonEnvelope.NONE,
            (name, buffer, offset, length) -> discarded.add(name));

        assertThat(sink, instanceOf(LlmAnthropicEncodeSink.class));
    }

    @Test
    public void shouldRoundTripResponseDecodeIntoAnotherDialectsResponseEncode()
    {
        LlmDialect anthropic = new LlmAnthropicDialect();
        LlmDialect openai = new LlmOpenaiDialect();
        List<Event> events = new ArrayList<>();

        JsonTransform decode = anthropic.supplyResponseDecodeTransform();
        JsonSink encode = openai.supplyResponseEncodeSink(JsonEnvelope.NONE, (name, buffer, offset, length) ->
            events.add(new Event(readBody(buffer, offset, length))));

        assertThat(decode, instanceOf(LlmAnthropicDecodeTransform.class));
        assertThat(encode, instanceOf(LlmOpenaiEncodeSink.class));

        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).transform(decode).into(encode);

        ((LlmDialectEvent) decode).event("message_start");
        String json = "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3\"," +
            "\"role\":\"assistant\",\"usage\":{\"input_tokens\":10}}}";
        byte[] bytes = json.getBytes(UTF_8);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertThat(status, equalTo(Status.COMPLETED));
        assertThat(events.size(), equalTo(1));
        JsonObject choice = events.get(0).body.getJsonArray("choices").getJsonObject(0);
        assertThat(choice.getJsonObject("delta").getString("role"), equalTo("assistant"));
        assertThat(events.get(0).body.getString("id"), equalTo("msg_1"));
        assertThat(events.get(0).body.getString("model"), equalTo("claude-3"));
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
        private final JsonObject body;

        private Event(
            JsonObject body)
        {
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
        return envelope;
    }

    private static DirectBufferEx value(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
