/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class OpenapiBindingConfigTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    private OpenapiBindingConfig newBindingConfig(
        String override)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    override,
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
        OpenapiBindingConfig binding = newBindingConfig("https://frontend.example.com/apis");

        assertEquals(URI.create("https://frontend.example.com:443/apis"), binding.resolveBaseURL("petstore"));
    }

    @Test
    public void shouldDefaultPortWhenOverrideOmitsIt()
    {
        OpenapiBindingConfig binding = newBindingConfig("http://dev.example.com");

        assertEquals(URI.create("http://dev.example.com:80"), binding.resolveBaseURL("petstore"));
    }

    @Test
    public void shouldThrowWhenNoOverrideConfigured()
    {
        OpenapiBindingConfig binding = newBindingConfig(null);

        assertThrows(NullPointerException.class, () -> binding.resolveBaseURL("petstore"));
    }

    @Test
    public void shouldThrowWhenSpecLabelDoesNotMatch()
    {
        OpenapiBindingConfig binding = newBindingConfig("https://frontend.example.com/apis");

        assertThrows(NoSuchElementException.class, () -> binding.resolveBaseURL("other-spec"));
    }
}
