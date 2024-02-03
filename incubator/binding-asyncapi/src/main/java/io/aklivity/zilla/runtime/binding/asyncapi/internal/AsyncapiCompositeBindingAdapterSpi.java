/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.mqtt.proxy.AsyncApiMqttProxyConfigGenerator;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;

public class AsyncapiCompositeBindingAdapterSpi implements CompositeBindingAdapterSpi
{
    private static final String COMPOSITE_MQTT_SERVER_NAMESPACE = "mqtt_server";
    private static final String COMPOSITE_MQTT_CLIENT_NAMESPACE = "mqtt_client";

    private final URI asyncapiRoot = new File(".").toURI();

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        AsyncapiBindingConfig asyncapiBinding = new AsyncapiBindingConfig(binding);
        BindingConfigBuilder<BindingConfig> builder = BindingConfig.builder(binding);
        final AsyncapiOptionsConfig options = asyncapiBinding.options;
        final List<URI> specs = options.specs;
        switch (binding.kind)
        {
        case SERVER:
            for (int i = 0; i < specs.size(); i++)
            {
                final URI resolved = asyncapiRoot.resolve(specs.get(i).getPath());
                try (InputStream input = new FileInputStream(resolved.getPath()))
                {
                    AsyncApiMqttProxyConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(input);
                    EngineConfig config = generator.createServerConfig(COMPOSITE_MQTT_SERVER_NAMESPACE + i);
                    config.namespaces.forEach(builder::composite);
                    //builder.vault()
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    rethrowUnchecked(e);
                }
            }
            return builder.build();
        case CLIENT:
            for (int i = 0; i < specs.size(); i++)
            {
                final URI resolved = asyncapiRoot.resolve(specs.get(i).getPath());
                try (InputStream input = new FileInputStream(resolved.getPath()))
                {
                    AsyncApiMqttProxyConfigGenerator generator = new AsyncApiMqttProxyConfigGenerator(input);
                    EngineConfig config = generator.createClientConfig(COMPOSITE_MQTT_CLIENT_NAMESPACE + i);
                    config.namespaces.forEach(builder::composite);
                    //builder.vault()
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    rethrowUnchecked(e);
                }
            }
            return builder.build();
        default:
            return binding;
        }
    }
}
