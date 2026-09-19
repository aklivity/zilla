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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

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
        LlmDialect dialect = factoriesByName.get("anthropic").create();

        assertThat(dialect, not(nullValue()));
        assertThat(dialect.name(), equalTo("anthropic"));
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
    public void shouldSupplyValidatorOnlyForRequestKind()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.supplyValidator(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE), not(nullValue()));
        assertThat(dialect.supplyValidator(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE).identity(), is(true));
        assertThat(dialect.supplyValidator(LlmDialect.Kind.RESPONSE, JsonEnvelope.NONE).identity(), is(true));
    }

    @Test
    public void shouldSupplySchemaValidatorForBothKinds()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        assertThat(dialect.supplySchemaValidator(LlmDialect.Kind.REQUEST), not(nullValue()));
        assertThat(dialect.supplySchemaValidator(LlmDialect.Kind.RESPONSE), not(nullValue()));
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
