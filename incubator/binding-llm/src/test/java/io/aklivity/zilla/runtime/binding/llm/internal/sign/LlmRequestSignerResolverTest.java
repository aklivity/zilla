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
package io.aklivity.zilla.runtime.binding.llm.internal.sign;

import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmSignConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSignerContext;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSignerFactorySpi;

public class LlmRequestSignerResolverTest
{
    private final LlmRequestSignerContext context = mock(LlmRequestSignerContext.class);

    @Test
    public void shouldResolveNullWhenSignNotConfigured()
    {
        LlmRequestSignerFactorySpi factory = factory("mock", mock(LlmRequestSigner.class));
        LlmRequestSignerResolver resolver = new LlmRequestSignerResolver(null, context, of(factory));

        LlmRequestSigner resolved = resolver.resolve();

        assertThat(resolved, nullValue());
        verify(factory, never()).create(any(), any());
    }

    @Test
    public void shouldResolveConfiguredSigner()
    {
        LlmRequestSigner signer = mock(LlmRequestSigner.class);
        LlmRequestSignerResolver resolver = new LlmRequestSignerResolver(sign("mock", null), context,
            of(factory("mock", signer)));

        LlmRequestSigner resolved = resolver.resolve();

        assertThat(resolved, equalTo(signer));
    }

    @Test
    public void shouldPassContextToFactory()
    {
        LlmRequestSignerFactorySpi factory = factory("mock", mock(LlmRequestSigner.class));
        LlmRequestSignerResolver resolver = new LlmRequestSignerResolver(sign("mock", null), context, of(factory));

        resolver.resolve();

        verify(factory).create(context, null);
    }

    @Test
    public void shouldPassParsedOptionsToFactory()
    {
        OptionsConfig options = mock(OptionsConfig.class);
        LlmRequestSignerFactorySpi factory = factory("mock", mock(LlmRequestSigner.class));
        LlmRequestSignerResolver resolver = new LlmRequestSignerResolver(sign("mock", options), context, of(factory));

        resolver.resolve();

        verify(factory).create(context, options);
    }

    @Test
    public void shouldReturnNullForUnregisteredSign()
    {
        LlmRequestSignerResolver resolver = new LlmRequestSignerResolver(sign("unregistered", null), context,
            of(factory("mock", mock(LlmRequestSigner.class))));

        LlmRequestSigner resolved = resolver.resolve();

        assertThat(resolved, nullValue());
    }

    private static LlmSignConfig sign(
        String name,
        OptionsConfig options)
    {
        return LlmSignConfig.builder()
            .name(name)
            .options(options)
            .build();
    }

    private static LlmRequestSignerFactorySpi factory(
        String name,
        LlmRequestSigner signer)
    {
        LlmRequestSignerFactorySpi factory = mock(LlmRequestSignerFactorySpi.class);
        when(factory.name()).thenReturn(name);
        when(factory.create(any(), any())).thenReturn(signer);
        return factory;
    }
}
