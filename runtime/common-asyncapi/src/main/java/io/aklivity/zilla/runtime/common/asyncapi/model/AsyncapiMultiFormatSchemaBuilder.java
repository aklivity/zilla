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

public final class AsyncapiMultiFormatSchemaBuilder<T>
    extends AbstractAsyncapiResolvableBuilder<T, AsyncapiMultiFormatSchemaBuilder<T>>
{
    private final Function<AsyncapiMultiFormatSchema, T> mapper;

    private String schemaFormat;
    private Object schema;

    AsyncapiMultiFormatSchemaBuilder(
        Function<AsyncapiMultiFormatSchema, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiMultiFormatSchemaBuilder<T>> thisType()
    {
        return (Class<AsyncapiMultiFormatSchemaBuilder<T>>) getClass();
    }

    public AsyncapiMultiFormatSchemaBuilder<T> schemaFormat(
        String schemaFormat)
    {
        this.schemaFormat = schemaFormat;
        return this;
    }

    public AsyncapiMultiFormatSchemaBuilder<T> schema(
        Object schema)
    {
        this.schema = schema;
        return this;
    }

    @Override
    public T build()
    {
        AsyncapiMultiFormatSchema schema = new AsyncapiMultiFormatSchema();
        schema.ref = ref;
        schema.schemaFormat = schemaFormat;
        schema.schema = this.schema;
        return mapper.apply(schema);
    }
}
