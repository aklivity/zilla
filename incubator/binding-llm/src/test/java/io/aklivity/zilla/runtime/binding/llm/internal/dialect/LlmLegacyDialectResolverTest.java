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

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialectContext;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

public class LlmLegacyDialectResolverTest
{
    private final LlmLegacyDialectContext context = mock(LlmLegacyDialectContext.class);

    @Test
    public void shouldDetectRegisteredDialectByHeaders()
    {
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver(null, context);
        JsonEnvelope headers = headers(":method", "POST", ":path", "/v1/messages");

        LlmLegacyDialect resolved = resolver.resolve(headers);

        assertThat(resolved, not(nullValue()));
        assertThat(resolved.name(), equalTo("anthropic"));
    }

    @Test
    public void shouldReturnNullWhenNoDialectDetected()
    {
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver(null, context);

        LlmLegacyDialect resolved = resolver.resolve(JsonEnvelope.NONE);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldResolveFixedDialectWithoutDetection()
    {
        LlmLegacyDialect dialect = dialect("mock");
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver("mock", of(dialect));
        JsonEnvelope headers = mock(JsonEnvelope.class);

        LlmLegacyDialect resolved = resolver.resolve(headers);

        assertThat(resolved, equalTo(dialect));
        verify(dialect, never()).detect(any());
    }

    @Test
    public void shouldReturnNullForUnregisteredFixedDialect()
    {
        LlmLegacyDialect dialect = dialect("mock");
        when(dialect.detect(any())).thenReturn(true);
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver("unregistered", of(dialect));
        JsonEnvelope headers = mock(JsonEnvelope.class);

        LlmLegacyDialect resolved = resolver.resolve(headers);

        assertThat(resolved, nullValue());
        verify(dialect, never()).detect(any());
    }

    @Test
    public void shouldReturnSoleMatchingDialect()
    {
        LlmLegacyDialect matching = dialect("matching");
        LlmLegacyDialect other = dialect("other");
        when(matching.detect(any())).thenReturn(true);
        when(other.detect(any())).thenReturn(false);
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver(null, of(matching, other));
        JsonEnvelope headers = mock(JsonEnvelope.class);

        LlmLegacyDialect resolved = resolver.resolve(headers);

        assertThat(resolved, equalTo(matching));
    }

    @Test
    public void shouldReturnNullWhenMultipleDialectsMatch()
    {
        LlmLegacyDialect first = dialect("first");
        LlmLegacyDialect second = dialect("second");
        when(first.detect(any())).thenReturn(true);
        when(second.detect(any())).thenReturn(true);
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver(null, of(first, second));
        JsonEnvelope headers = mock(JsonEnvelope.class);

        LlmLegacyDialect resolved = resolver.resolve(headers);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldReturnDialectNamed()
    {
        LlmLegacyDialect dialect = dialect("mock");
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver(null, of(dialect));

        LlmLegacyDialect resolved = resolver.dialectNamed("mock");

        assertThat(resolved, equalTo(dialect));
    }

    @Test
    public void shouldReturnNullForUnregisteredDialectName()
    {
        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver(null, of(dialect("mock")));

        LlmLegacyDialect resolved = resolver.dialectNamed("unregistered");

        assertThat(resolved, nullValue());
    }

    private static LlmLegacyDialect dialect(
        String name)
    {
        LlmLegacyDialect dialect = mock(LlmLegacyDialect.class);
        when(dialect.name()).thenReturn(name);
        return dialect;
    }

    private static JsonEnvelope headers(
        String name1,
        String value1,
        String name2,
        String value2)
    {
        JsonEnvelope headers = mock(JsonEnvelope.class);
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
