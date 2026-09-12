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

import io.aklivity.zilla.runtime.binding.llm.dialect.HttpHeaders;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;

public class LlmDialectResolverTest
{
    @Test
    public void shouldDetectRegisteredDialectByHeader()
    {
        LlmDialectResolver resolver = new LlmDialectResolver(null);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.header("x-llm-dialect")).thenReturn("test");

        LlmDialect resolved = resolver.resolve("/v1/chat", headers);

        assertThat(resolved, not(nullValue()));
        assertThat(resolved.name(), equalTo("test"));
    }

    @Test
    public void shouldReturnNullWhenNoDialectDetected()
    {
        LlmDialectResolver resolver = new LlmDialectResolver(null);
        HttpHeaders headers = mock(HttpHeaders.class);

        LlmDialect resolved = resolver.resolve("/v1/chat", headers);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldResolveFixedDialectWithoutDetection()
    {
        LlmDialect dialect = dialect("mock");
        LlmDialectResolver resolver = new LlmDialectResolver("mock", of(dialect));
        HttpHeaders headers = mock(HttpHeaders.class);

        LlmDialect resolved = resolver.resolve("/anything", headers);

        assertThat(resolved, equalTo(dialect));
        verify(dialect, never()).detect(any(), any());
    }

    @Test
    public void shouldReturnNullForUnregisteredFixedDialect()
    {
        LlmDialect dialect = dialect("mock");
        when(dialect.detect(any(), any())).thenReturn(true);
        LlmDialectResolver resolver = new LlmDialectResolver("unregistered", of(dialect));
        HttpHeaders headers = mock(HttpHeaders.class);

        LlmDialect resolved = resolver.resolve("/anything", headers);

        assertThat(resolved, nullValue());
        verify(dialect, never()).detect(any(), any());
    }

    @Test
    public void shouldReturnSoleMatchingDialect()
    {
        LlmDialect matching = dialect("matching");
        LlmDialect other = dialect("other");
        when(matching.detect(any(), any())).thenReturn(true);
        when(other.detect(any(), any())).thenReturn(false);
        LlmDialectResolver resolver = new LlmDialectResolver(null, of(matching, other));
        HttpHeaders headers = mock(HttpHeaders.class);

        LlmDialect resolved = resolver.resolve("/v1/chat", headers);

        assertThat(resolved, equalTo(matching));
    }

    @Test
    public void shouldReturnNullWhenMultipleDialectsMatch()
    {
        LlmDialect first = dialect("first");
        LlmDialect second = dialect("second");
        when(first.detect(any(), any())).thenReturn(true);
        when(second.detect(any(), any())).thenReturn(true);
        LlmDialectResolver resolver = new LlmDialectResolver(null, of(first, second));
        HttpHeaders headers = mock(HttpHeaders.class);

        LlmDialect resolved = resolver.resolve("/v1/chat", headers);

        assertThat(resolved, nullValue());
    }

    private static LlmDialect dialect(
        String name)
    {
        LlmDialect dialect = mock(LlmDialect.class);
        when(dialect.name()).thenReturn(name);
        return dialect;
    }
}
