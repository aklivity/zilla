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

import java.util.Map;
import java.util.function.Function;

public final class AsyncapiMessageBuilder<T> extends AbstractAsyncapiResolvableBuilder<T, AsyncapiMessageBuilder<T>>
{
    private final Function<AsyncapiMessage, T> mapper;

    private String contentType;
    private AsyncapiSchemaItem payload;
    private Map<String, Object> bindings;
    private Map<String, Object> extensions;

    AsyncapiMessageBuilder(
        Function<AsyncapiMessage, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiMessageBuilder<T>> thisType()
    {
        return (Class<AsyncapiMessageBuilder<T>>) getClass();
    }

    public AsyncapiMessageBuilder<T> contentType(
        String contentType)
    {
        this.contentType = contentType;
        return this;
    }

    public AsyncapiMultiFormatSchemaBuilder<AsyncapiMessageBuilder<T>> payload()
    {
        return AsyncapiMultiFormatSchema.builder(this::payload);
    }

    public AsyncapiMessageBuilder<T> payload(
        AsyncapiSchemaItem payload)
    {
        this.payload = payload;
        return this;
    }

    public AsyncapiMessageBuilder<T> bindings(
        Map<String, Object> bindings)
    {
        this.bindings = bindings;
        return this;
    }

    public AsyncapiMessageBuilder<T> extensions(
        Map<String, Object> extensions)
    {
        this.extensions = extensions;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiMessage message = new AsyncapiMessage();
        message.ref = ref;
        message.contentType = contentType;
        message.payload = payload;
        message.bindings = bindings;
        message.extensions = extensions;
        return mapper.apply(message);
    }
}
