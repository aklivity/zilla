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
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public class LlmDialectFactorySpiTest
{
    private final Map<String, LlmDialectFactorySpi> factoriesByName = ServiceLoader
        .load(LlmDialectFactorySpi.class)
        .stream()
        .map(Supplier::get)
        .collect(toMap(LlmDialectFactorySpi::name, identity()));

    @Test
    public void shouldResolveRegisteredDialect()
    {
        assertThat(factoriesByName.keySet(), hasItem("test"));
    }

    @Test
    public void shouldCreateMatchingDialect()
    {
        LlmDialect dialect = factoriesByName.get("test").create();

        assertThat(dialect, not(nullValue()));
        assertThat(dialect.name(), equalTo("test"));
        assertThat(dialect.contentType(), equalTo("application/test+json"));
    }

    @Test
    public void shouldDetectByHeader()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header("x-llm-dialect")).thenReturn("test");

        assertThat(dialect.detect("/v1/chat", headers), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedHeader()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        HttpHeaders headers = mock(HttpHeaders.class);

        assertThat(dialect.detect("/v1/chat", headers), is(false));
    }

    @Test
    public void shouldSupplyIdentityDecoderForEachKind()
    {
        LlmDialect dialect = factoriesByName.get("test").create();

        for (LlmDialect.Kind kind : LlmDialect.Kind.values())
        {
            JsonTransform decoder = dialect.supplyDecoder(kind);

            assertThat(decoder.identity(), is(true));
            verifyForwardsToSink(decoder);
        }
    }

    @Test
    public void shouldSupplyIdentityEncoderForEachKind()
    {
        LlmDialect dialect = factoriesByName.get("test").create();

        for (LlmDialect.Kind kind : LlmDialect.Kind.values())
        {
            JsonTransform encoder = dialect.supplyEncoder(kind);

            assertThat(encoder.identity(), is(true));
            verifyForwardsToSink(encoder);
        }
    }

    @Test
    public void shouldConvertKindValueOf()
    {
        assertThat(LlmDialect.Kind.valueOf("REQUEST"), equalTo(LlmDialect.Kind.REQUEST));
        assertThat(LlmDialect.Kind.valueOf("RESPONSE"), equalTo(LlmDialect.Kind.RESPONSE));
    }

    @Test
    public void shouldReturnKindValues()
    {
        assertThat(LlmDialect.Kind.values(), arrayContaining(LlmDialect.Kind.REQUEST, LlmDialect.Kind.RESPONSE));
    }

    private static void verifyForwardsToSink(
        JsonTransform transform)
    {
        JsonController control = mock(JsonController.class);
        JsonSource source = mock(JsonSource.class);
        JsonSink sink = mock(JsonSink.class);
        when(sink.transform(control, source, JsonEvent.VALUE_STRING)).thenReturn(JsonPipeline.Status.ADVANCED);

        JsonPipeline.Status status = transform.transform(control, source, JsonEvent.VALUE_STRING, sink);

        assertThat(status, equalTo(JsonPipeline.Status.ADVANCED));
        verify(sink).transform(control, source, JsonEvent.VALUE_STRING);
    }
}
