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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class AsyncapiChannelBuilder<T> extends AbstractAsyncapiResolvableBuilder<T, AsyncapiChannelBuilder<T>>
{
    private final Function<AsyncapiChannel, T> mapper;

    private String address;
    private LinkedHashMap<String, AsyncapiMessage> messages;
    private Map<String, Object> bindings;
    private Map<String, Object> extensions;

    AsyncapiChannelBuilder(
        Function<AsyncapiChannel, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiChannelBuilder<T>> thisType()
    {
        return (Class<AsyncapiChannelBuilder<T>>) getClass();
    }

    public AsyncapiChannelBuilder<T> address(
        String address)
    {
        this.address = address;
        return this;
    }

    public AsyncapiMessageBuilder<AsyncapiChannelBuilder<T>> message(
        String name)
    {
        return AsyncapiMessage.builder(message -> message(name, message));
    }

    public AsyncapiChannelBuilder<T> message(
        String name,
        AsyncapiMessage message)
    {
        if (messages == null)
        {
            messages = new LinkedHashMap<>();
        }
        messages.put(name, message);
        return this;
    }

    public AsyncapiChannelBuilder<T> messages(
        LinkedHashMap<String, AsyncapiMessage> messages)
    {
        this.messages = messages;
        return this;
    }

    public AsyncapiChannelBuilder<T> bindings(
        Map<String, Object> bindings)
    {
        this.bindings = bindings;
        return this;
    }

    public AsyncapiChannelBuilder<T> extensions(
        Map<String, Object> extensions)
    {
        this.extensions = extensions;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiChannel channel = new AsyncapiChannel();
        channel.ref = ref;
        channel.address = address;
        channel.messages = messages;
        channel.bindings = bindings;
        channel.extensions = extensions;
        return mapper.apply(channel);
    }
}
