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
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

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
    }

    @Test
    public void shouldDetectMatchingRequest()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope headers = headers(":method", "POST", ":path", "/v1/test");

        assertThat(dialect.detect(headers), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedPath()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope headers = headers(":method", "POST", ":path", "/v1/other");

        assertThat(dialect.detect(headers), is(false));
    }

    @Test
    public void shouldNotDetectWrongMethod()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope headers = headers(":method", "GET", ":path", "/v1/test");

        assertThat(dialect.detect(headers), is(false));
    }

    @Test
    public void shouldNotDetectWithNoHeaders()
    {
        LlmDialect dialect = factoriesByName.get("test").create();

        assertThat(dialect.detect(ModelEnvelope.NONE), is(false));
    }

    @Test
    public void shouldExtractModelNameOnRequestDecode()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope envelope = mock(ModelEnvelope.class);
        ModelTransform decoder = dialect.supplyDecoder(LlmDialect.Kind.REQUEST, envelope);

        DirectBufferEx modelValue = buffer("gpt-4");
        ModelStatus status = fieldEvent(decoder, "$.model", modelValue);

        assertThat(status, equalTo(ModelStatus.OK));
        assertThat(decoder.identity(), is(true));
        verify(envelope).set("model", modelValue);
    }

    @Test
    public void shouldNotExtractUnrelatedFieldOnRequestDecode()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope envelope = mock(ModelEnvelope.class);
        ModelTransform decoder = dialect.supplyDecoder(LlmDialect.Kind.REQUEST, envelope);

        fieldEvent(decoder, "$.other", buffer("ignored"));

        verify(envelope, never()).set(anyString(), any());
    }

    @Test
    public void shouldForwardResponseDecodeUnchanged()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope envelope = mock(ModelEnvelope.class);

        ModelTransform decoder = dialect.supplyDecoder(LlmDialect.Kind.RESPONSE, envelope);

        assertThat(decoder, equalTo(ModelTransform.NONE));
    }

    @Test
    public void shouldSupplyIdentityEncoderForEachKind()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        ModelEnvelope envelope = mock(ModelEnvelope.class);

        for (LlmDialect.Kind kind : LlmDialect.Kind.values())
        {
            ModelTransform encoder = dialect.supplyEncoder(kind, envelope);

            assertThat(encoder, equalTo(ModelTransform.NONE));
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

    private static ModelStatus fieldEvent(
        ModelTransform transform,
        String path,
        DirectBufferEx value)
    {
        ModelController control = mock(ModelController.class);
        ModelSource source = mock(ModelSource.class);
        when(source.getPath()).thenReturn(path);
        when(source.getValue()).thenReturn(value);
        ModelSink sink = mock(ModelSink.class);
        when(sink.transform(control, source, ModelEvent.FIELD)).thenReturn(ModelStatus.OK);

        ModelStatus status = transform.transform(control, source, ModelEvent.FIELD, sink);

        verify(sink).transform(control, source, ModelEvent.FIELD);

        return status;
    }

    private static ModelEnvelope headers(
        String name1,
        String value1,
        String name2,
        String value2)
    {
        ModelEnvelope headers = mock(ModelEnvelope.class);
        when(headers.get(name1, 0)).thenReturn(buffer(value1));
        when(headers.get(name2, 0)).thenReturn(buffer(value2));
        return headers;
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
