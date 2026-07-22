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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class OpenapiBindingConfigTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    private OpenapiBindingConfig newBindingConfig(
        List<String> overrides)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    overrides,
                    List.of(new OpenapiCatalogConfig("catalog0", "test", "latest")),
                    null))
                .build())
            .exit("openapi0")
            .build();
        binding.resolveId = name -> 1L;

        return new OpenapiBindingConfig(
            context, binding, new HttpBeginExFW(), new HttpBeginExFW.Builder(),
            new UnsafeBufferEx(new byte[1024]), 0);
    }

    @Test
    public void shouldResolveBaseURLFromOverride()
    {
        OpenapiBindingConfig binding = newBindingConfig(List.of("https://frontend.example.com/apis"));

        assertThat(binding.resolveBaseURLs("petstore"), contains(URI.create("https://frontend.example.com:443/apis")));
    }

    @Test
    public void shouldDefaultPortWhenOverrideOmitsIt()
    {
        OpenapiBindingConfig binding = newBindingConfig(List.of("http://dev.example.com"));

        assertThat(binding.resolveBaseURLs("petstore"), contains(URI.create("http://dev.example.com:80")));
    }

    @Test
    public void shouldResolveMultipleOverrides()
    {
        OpenapiBindingConfig binding = newBindingConfig(
            List.of("http://localhost:8080", "http://localhost:9090"));

        assertThat(binding.resolveBaseURLs("petstore"), contains(
            URI.create("http://localhost:8080"), URI.create("http://localhost:9090")));
    }

    @Test
    public void shouldThrowWhenNoOverrideConfigured()
    {
        OpenapiBindingConfig binding = newBindingConfig(null);

        assertThrows(NullPointerException.class, () -> binding.resolveBaseURLs("petstore"));
    }

    @Test
    public void shouldThrowWhenSpecLabelDoesNotMatch()
    {
        OpenapiBindingConfig binding = newBindingConfig(List.of("https://frontend.example.com/apis"));

        assertThrows(NoSuchElementException.class, () -> binding.resolveBaseURLs("other-spec"));
    }
}
