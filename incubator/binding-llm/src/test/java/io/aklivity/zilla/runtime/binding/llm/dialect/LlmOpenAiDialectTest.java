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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.junit.Test;

public class LlmOpenAiDialectTest
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
    public void shouldResolveRequestContentTypeAsJsonRegardlessOfStreamFlag()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpRequestBody streaming = mock(HttpRequestBody.class);
        when(streaming.value("stream")).thenReturn("true");

        assertThat(dialect.contentType(LlmDialect.Kind.REQUEST, null, null), equalTo("application/json"));
        assertThat(dialect.contentType(LlmDialect.Kind.REQUEST, null, streaming), equalTo("application/json"));
    }

    @Test
    public void shouldResolveResponseContentTypeAsSseWhenStreaming()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpRequestBody body = mock(HttpRequestBody.class);
        when(body.value("stream")).thenReturn("true");

        assertThat(dialect.contentType(LlmDialect.Kind.RESPONSE, null, body), equalTo("text/event-stream"));
    }

    @Test
    public void shouldResolveResponseContentTypeAsJsonWhenNotStreaming()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpRequestBody body = mock(HttpRequestBody.class);
        when(body.value("stream")).thenReturn("false");

        assertThat(dialect.contentType(LlmDialect.Kind.RESPONSE, null, body), equalTo("application/json"));
    }

    @Test
    public void shouldResolveResponseContentTypeAsJsonWhenBodyUnavailable()
    {
        LlmDialect dialect = new LlmOpenAiDialect();

        assertThat(dialect.contentType(LlmDialect.Kind.RESPONSE, null, null), equalTo("application/json"));
    }

    @Test
    public void shouldDetectChatCompletionsPost()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header(":method")).thenReturn("POST");

        assertThat(dialect.detect("/v1/chat/completions", headers), is(true));
    }

    @Test
    public void shouldDetectCompletionsPost()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header(":method")).thenReturn("POST");

        assertThat(dialect.detect("/v1/completions", headers), is(true));
    }

    @Test
    public void shouldDetectRegardlessOfMethodCase()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header(":method")).thenReturn("post");

        assertThat(dialect.detect("/v1/chat/completions", headers), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedPath()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header(":method")).thenReturn("POST");

        assertThat(dialect.detect("/v1/embeddings", headers), is(false));
    }

    @Test
    public void shouldNotDetectNonPostMethod()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header(":method")).thenReturn("GET");

        assertThat(dialect.detect("/v1/chat/completions", headers), is(false));
    }

    @Test
    public void shouldNotDetectWithNullHeaders()
    {
        LlmDialect dialect = new LlmOpenAiDialect();

        assertThat(dialect.detect("/v1/chat/completions", null), is(false));
    }

    @Test
    public void shouldNotDetectWithNullPath()
    {
        LlmDialect dialect = new LlmOpenAiDialect();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header(":method")).thenReturn("POST");

        assertThat(dialect.detect(null, headers), is(false));
    }

    @Test
    public void shouldSupplyDecodersAndEncodersForEachKind()
    {
        LlmDialect dialect = new LlmOpenAiDialect();

        for (LlmDialect.Kind kind : LlmDialect.Kind.values())
        {
            assertThat(dialect.supplyDecoder(kind), not(nullValue()));
            assertThat(dialect.supplyEncoder(kind), not(nullValue()));
            assertThat(dialect.supplyDecoder(kind).identity(), is(false));
            assertThat(dialect.supplyEncoder(kind).identity(), is(false));
        }
    }
}
