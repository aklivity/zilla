/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tls.internal;

import static io.aklivity.zilla.runtime.engine.config.RoleConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.RoleConfig.PROXY;
import static io.aklivity.zilla.runtime.engine.config.RoleConfig.SERVER;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.tls.internal.stream.TlsClientFactory;
import io.aklivity.zilla.runtime.binding.tls.internal.stream.TlsProxyFactory;
import io.aklivity.zilla.runtime.binding.tls.internal.stream.TlsServerFactory;
import io.aklivity.zilla.runtime.binding.tls.internal.stream.TlsStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.RoleConfig;

final class TlsBindingContext implements BindingContext
{
    private final Map<RoleConfig, TlsStreamFactory> factories;

    TlsBindingContext(
        TlsConfiguration config,
        EngineContext context)
    {
        Map<RoleConfig, TlsStreamFactory> factories = new EnumMap<>(RoleConfig.class);
        factories.put(SERVER, new TlsServerFactory(config, context));
        factories.put(PROXY, new TlsProxyFactory(config, context));
        factories.put(CLIENT, new TlsClientFactory(config, context));
        this.factories = factories;
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        TlsStreamFactory factory = factories.get(binding.kind);
        if (factory != null)
        {
            factory.attach(binding);
        }
        return factory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        TlsStreamFactory factory = factories.get(binding.kind);
        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }
}
