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
package io.aklivity.zilla.runtime.binding.llm.internal.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

public class LlmDialectResolverTest
{
    @Test
    public void shouldDetectRegisteredDialectByHeaders()
    {
        LlmDialectResolver resolver = new LlmDialectResolver(null);
        ModelEnvelope headers = headers(":method", "POST", ":path", "/v1/test");

        LlmDialect resolved = resolver.resolve(headers);

        assertThat(resolved, not(nullValue()));
        assertThat(resolved.name(), equalTo("test"));
    }

    @Test
    public void shouldReturnNullWhenNoDialectDetected()
    {
        LlmDialectResolver resolver = new LlmDialectResolver(null);

        LlmDialect resolved = resolver.resolve(ModelEnvelope.NONE);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldResolveFixedDialectWithoutDetection()
    {
        LlmDialect dialect = dialect("mock");
        LlmDialectResolver resolver = new LlmDialectResolver("mock", of(dialect));
        ModelEnvelope headers = mock(ModelEnvelope.class);

        LlmDialect resolved = resolver.resolve(headers);

        assertThat(resolved, equalTo(dialect));
        verify(dialect, never()).detect(any());
    }

    @Test
    public void shouldReturnNullForUnregisteredFixedDialect()
    {
        LlmDialect dialect = dialect("mock");
        when(dialect.detect(any())).thenReturn(true);
        LlmDialectResolver resolver = new LlmDialectResolver("unregistered", of(dialect));
        ModelEnvelope headers = mock(ModelEnvelope.class);

        LlmDialect resolved = resolver.resolve(headers);

        assertThat(resolved, nullValue());
        verify(dialect, never()).detect(any());
    }

    @Test
    public void shouldReturnSoleMatchingDialect()
    {
        LlmDialect matching = dialect("matching");
        LlmDialect other = dialect("other");
        when(matching.detect(any())).thenReturn(true);
        when(other.detect(any())).thenReturn(false);
        LlmDialectResolver resolver = new LlmDialectResolver(null, of(matching, other));
        ModelEnvelope headers = mock(ModelEnvelope.class);

        LlmDialect resolved = resolver.resolve(headers);

        assertThat(resolved, equalTo(matching));
    }

    @Test
    public void shouldReturnNullWhenMultipleDialectsMatch()
    {
        LlmDialect first = dialect("first");
        LlmDialect second = dialect("second");
        when(first.detect(any())).thenReturn(true);
        when(second.detect(any())).thenReturn(true);
        LlmDialectResolver resolver = new LlmDialectResolver(null, of(first, second));
        ModelEnvelope headers = mock(ModelEnvelope.class);

        LlmDialect resolved = resolver.resolve(headers);

        assertThat(resolved, nullValue());
    }

    private static LlmDialect dialect(
        String name)
    {
        LlmDialect dialect = mock(LlmDialect.class);
        when(dialect.name()).thenReturn(name);
        return dialect;
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
