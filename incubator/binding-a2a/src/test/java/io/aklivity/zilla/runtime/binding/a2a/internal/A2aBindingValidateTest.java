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
package io.aklivity.zilla.runtime.binding.a2a.internal;

import static org.junit.Assert.assertThrows;

import java.io.IOException;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aBindingConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.ConfigException;
import io.aklivity.zilla.config.engine.EngineConfig;
import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.search.A2aUnregisteredSearchIndexConfig;
import io.aklivity.zilla.runtime.engine.Configuration;

public class A2aBindingValidateTest
{
    private static final String CONFIG_ROOT = "io/aklivity/zilla/specs/binding/a2a/config/";

    private final A2aBinding binding = new A2aBinding(new A2aConfiguration(new Configuration()));

    @Test
    public void shouldAcceptDiscoveryAgentDistinctFromRoutedAgents() throws Exception
    {
        binding.validate(bindingOf("proxy.discovery.cache.yaml"));
    }

    @Test
    public void shouldRejectDiscoveryAgentCollidingWithRoutedAgent() throws Exception
    {
        final BindingConfig config = bindingOf("proxy.discovery.agent.collision.invalid.yaml");

        assertThrows(ConfigException.class, () -> binding.validate(config));
    }

    @Test
    public void shouldRejectDiscoveryWithoutCache() throws Exception
    {
        final BindingConfig config = bindingOf("proxy.discovery.cache.missing.invalid.yaml");

        assertThrows(ConfigException.class, () -> binding.validate(config));
    }

    @Test
    public void shouldRejectDiscoverySearchIndexTypeWithoutFactory()
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .options()
                .cache()
                    .store("memory0")
                    .build()
                .discovery()
                    .search()
                        .index(new A2aUnregisteredSearchIndexConfig())
                        .build()
                    .build()
                .build()
            .build();

        assertThrows(ConfigException.class, () -> binding.validate(config));
    }

    @Test
    public void shouldAcceptProxyWithoutDiscovery() throws Exception
    {
        binding.validate(bindingOf("proxy.cache.yaml"));
    }

    private static BindingConfig bindingOf(
        String name) throws IOException
    {
        final EngineConfigReader reader = new EngineConfigReader(
            text -> text, new EngineInfo(), A2aBindingValidateTest::noop, A2aBindingValidateTest::noop);

        try (var input = A2aBindingValidateTest.class.getClassLoader().getResourceAsStream(CONFIG_ROOT + name))
        {
            final EngineConfig config = reader.read(new String(input.readAllBytes()));
            return config.namespaces.get(0).bindings.get(0);
        }
    }

    private static void noop(
        String value)
    {
    }
}
