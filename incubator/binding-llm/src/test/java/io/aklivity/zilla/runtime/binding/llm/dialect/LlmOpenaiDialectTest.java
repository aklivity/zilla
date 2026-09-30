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

public class LlmOpenaiDialectTest
{
    private final Map<String, LlmDialectFactorySpi> factoriesByName = ServiceLoader
        .load(LlmDialectFactorySpi.class)
        .stream()
        .map(Supplier::get)
        .collect(toMap(LlmDialectFactorySpi::name, identity()));

    @Test
    public void shouldResolveRegisteredDialect()
    {
        assertThat(factoriesByName.keySet(), hasItem("openai"));
    }

    @Test
    public void shouldCreateMatchingDialect()
    {
        LlmDialect dialect = factoriesByName.get("openai").create();

        assertThat(dialect, not(nullValue()));
        assertThat(dialect.name(), equalTo("openai"));
    }

    @Test
    public void shouldResolveRequestPathUnderConfiguredBasePath()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.requestPath("/v1"), equalTo("/v1/chat/completions"));
        assertThat(dialect.requestPath("/aicomp/v1"), equalTo("/aicomp/v1/chat/completions"));
    }

    @Test
    public void shouldDetectChatCompletionsPost()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.detect(headers("POST", "/v1/chat/completions")), is(true));
    }

    @Test
    public void shouldDetectCompletionsPost()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.detect(headers("POST", "/v1/completions")), is(true));
    }

    @Test
    public void shouldDetectRegardlessOfMethodCase()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.detect(headers("post", "/v1/chat/completions")), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedPath()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.detect(headers("POST", "/v1/embeddings")), is(false));
    }

    @Test
    public void shouldNotDetectNonPostMethod()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.detect(headers("GET", "/v1/chat/completions")), is(false));
    }

    @Test
    public void shouldNotDetectMismatchedContentType()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/chat/completions"));
        envelope.set("content-type", value("application/vnd.zilla.test-unknown+json"));

        assertThat(dialect.detect(envelope), is(false));
    }

    @Test
    public void shouldNotDetectMissingContentType()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        TestJsonEnvelope envelope = new TestJsonEnvelope();
        envelope.set(":method", value("POST"));
        envelope.set(":path", value("/v1/chat/completions"));

        assertThat(dialect.detect(envelope), is(false));
    }

    @Test
    public void shouldNotDetectWithEmptyHeaders()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.detect(JsonEnvelope.NONE), is(false));
    }

    @Test
    public void shouldSupplyDecodersAndEncodersForEachKind()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

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
        LlmDialect dialect = new LlmOpenaiDialect();

        assertThat(dialect.supplyExtractor(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE), not(nullValue()));
        assertThat(dialect.supplyExtractor(LlmDialect.Kind.REQUEST, JsonEnvelope.NONE).identity(), is(true));
        assertThat(dialect.supplyExtractor(LlmDialect.Kind.RESPONSE, JsonEnvelope.NONE).identity(), is(true));
    }

    @Test
    public void shouldSupplySchemaValidatorForBothKinds()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

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
        envelope.set("content-type", value("application/json"));
        return envelope;
    }

    private static DirectBufferEx value(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
