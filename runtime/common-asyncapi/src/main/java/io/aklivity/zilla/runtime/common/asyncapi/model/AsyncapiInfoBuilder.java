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

import java.util.function.Function;

public final class AsyncapiInfoBuilder<T> extends AsyncapiModelBuilder<T, AsyncapiInfoBuilder<T>>
{
    private final Function<AsyncapiInfo, T> mapper;

    private String title;
    private String version;
    private String description;

    AsyncapiInfoBuilder(
        Function<AsyncapiInfo, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiInfoBuilder<T>> thisType()
    {
        return (Class<AsyncapiInfoBuilder<T>>) getClass();
    }

    public AsyncapiInfoBuilder<T> title(
        String title)
    {
        this.title = title;
        return this;
    }

    public AsyncapiInfoBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public AsyncapiInfoBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiInfo info = new AsyncapiInfo();
        info.title = title;
        info.version = version;
        info.description = description;
        return mapper.apply(info);
    }
}
