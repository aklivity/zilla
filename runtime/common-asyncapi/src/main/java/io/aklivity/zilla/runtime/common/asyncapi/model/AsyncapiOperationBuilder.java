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

public final class AsyncapiOperationBuilder<T> extends AbstractAsyncapiResolvableBuilder<T, AsyncapiOperationBuilder<T>>
{
    private final Function<AsyncapiOperation, T> mapper;

    private AsyncapiChannel channel;
    private String action;
    private String summary;
    private Map<String, Object> bindings;
    private Map<String, Object> extensions;

    AsyncapiOperationBuilder(
        Function<AsyncapiOperation, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiOperationBuilder<T>> thisType()
    {
        return (Class<AsyncapiOperationBuilder<T>>) getClass();
    }

    public AsyncapiChannelBuilder<AsyncapiOperationBuilder<T>> channel()
    {
        return AsyncapiChannel.builder(this::channel);
    }

    public AsyncapiOperationBuilder<T> channel(
        AsyncapiChannel channel)
    {
        this.channel = channel;
        return this;
    }

    public AsyncapiOperationBuilder<T> action(
        String action)
    {
        this.action = action;
        return this;
    }

    public AsyncapiOperationBuilder<T> summary(
        String summary)
    {
        this.summary = summary;
        return this;
    }

    public AsyncapiOperationBuilder<T> bindings(
        Map<String, Object> bindings)
    {
        this.bindings = bindings;
        return this;
    }

    public AsyncapiOperationBuilder<T> extensions(
        Map<String, Object> extensions)
    {
        this.extensions = extensions;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiOperation operation = new AsyncapiOperation();
        operation.ref = ref;
        operation.channel = channel;
        operation.action = action;
        operation.summary = summary;
        operation.bindings = bindings;
        operation.extensions = extensions;
        return mapper.apply(operation);
    }
}
