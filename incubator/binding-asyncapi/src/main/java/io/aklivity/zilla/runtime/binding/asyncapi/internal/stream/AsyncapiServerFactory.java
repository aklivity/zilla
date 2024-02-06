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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.stream;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfiguration;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.mqtt.proxy.AsyncApiMqttProxyConfigGenerator;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;

public final class AsyncapiServerFactory implements AsyncapiStreamFactory
{
    private static final int FLAG_FIN = 1;
    private static final int FLAG_INIT = 2;
    private static final int FLAG_INCOMPLETE = 4;
    private static final int FLAG_INIT_INCOMPLETE = FLAG_INIT | FLAG_INCOMPLETE;
    private static final int FLAG_INIT_AND_FIN = FLAG_INIT | FLAG_FIN;

    private final int asyncapiTypeId;
    private final Long2ObjectHashMap<AsyncapiBindingConfig> bindings;
    public AsyncapiServerFactory(
        AsyncapiConfiguration config,
        EngineContext context)
    {
        this.asyncapiTypeId = context.supplyTypeId(AsyncapiBinding.NAME);
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        AsyncapiBindingConfig asyncapiBinding = new AsyncapiBindingConfig(binding);
        bindings.put(binding.id, asyncapiBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        return null;
    }
}
