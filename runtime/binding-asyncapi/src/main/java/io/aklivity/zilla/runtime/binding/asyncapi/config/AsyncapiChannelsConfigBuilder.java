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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
public class AsyncapiChannelsConfigBuilder<T> extends ConfigBuilder<T, AsyncapiChannelsConfigBuilder<T>>
{
    private final Function<AsyncapiChannelsConfig, T> mapper;

    private String sessions;
    private String messages;
    private String retained;
    AsyncapiChannelsConfigBuilder(
        Function<AsyncapiChannelsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiChannelsConfigBuilder<T>> thisType()
    {
        return (Class<AsyncapiChannelsConfigBuilder<T>>) getClass();
    }


    public AsyncapiChannelsConfigBuilder<T> sessions(
        String sessions)
    {
        this.sessions = sessions;
        return this;
    }

    public AsyncapiChannelsConfigBuilder<T> messages(
        String messages)
    {
        this.messages = messages;
        return this;
    }

    public AsyncapiChannelsConfigBuilder<T> retained(
        String retained)
    {
        this.retained = retained;
        return this;
    }


    @Override
    public T build()
    {
        return mapper.apply(
            new AsyncapiChannelsConfig(sessions, messages, retained));
    }
}
