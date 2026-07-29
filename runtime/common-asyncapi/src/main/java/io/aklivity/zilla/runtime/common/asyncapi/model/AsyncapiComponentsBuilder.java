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

public final class AsyncapiComponentsBuilder<T> extends AsyncapiModelBuilder<T, AsyncapiComponentsBuilder<T>>
{
    private final Function<AsyncapiComponents, T> mapper;

    private Map<String, AsyncapiMessage> messages;
    private Map<String, AsyncapiSchemaItem> schemas;
    private Map<String, Object> extensions;

    AsyncapiComponentsBuilder(
        Function<AsyncapiComponents, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiComponentsBuilder<T>> thisType()
    {
        return (Class<AsyncapiComponentsBuilder<T>>) getClass();
    }

    public AsyncapiMessageBuilder<AsyncapiComponentsBuilder<T>> message(
        String name)
    {
        return AsyncapiMessage.builder(message -> message(name, message));
    }

    public AsyncapiComponentsBuilder<T> message(
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

    public AsyncapiComponentsBuilder<T> messages(
        Map<String, AsyncapiMessage> messages)
    {
        this.messages = messages;
        return this;
    }

    public AsyncapiMultiFormatSchemaBuilder<AsyncapiComponentsBuilder<T>> schema(
        String name)
    {
        return AsyncapiMultiFormatSchema.builder(schema -> schema(name, schema));
    }

    public AsyncapiComponentsBuilder<T> schema(
        String name,
        AsyncapiSchemaItem schema)
    {
        if (schemas == null)
        {
            schemas = new LinkedHashMap<>();
        }
        schemas.put(name, schema);
        return this;
    }

    public AsyncapiComponentsBuilder<T> schemas(
        Map<String, AsyncapiSchemaItem> schemas)
    {
        this.schemas = schemas;
        return this;
    }

    public AsyncapiComponentsBuilder<T> extensions(
        Map<String, Object> extensions)
    {
        this.extensions = extensions;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiComponents components = new AsyncapiComponents();
        components.messages = messages;
        components.schemas = schemas;
        components.extensions = extensions;
        return mapper.apply(components);
    }
}
