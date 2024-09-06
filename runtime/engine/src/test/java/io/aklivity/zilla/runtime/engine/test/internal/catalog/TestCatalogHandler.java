/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.catalog;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;

public class TestCatalogHandler implements CatalogHandler
{
    private final String schema;
    private final int id;
    private final DirectBuffer prefix;

    public TestCatalogHandler(
        TestCatalogOptionsConfig options)
    {
        this.id = options != null ? options.id : NO_SCHEMA_ID;
        this.schema = options != null ? options.schema : null;
        this.prefix = options != null ? new String8FW(options.prefix).value() : null;
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        return id;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return schemaId == id ? schema : null;
    }

    @Override
    public int decode(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Decoder decoder)
    {
        int offset = prefix != null ? prefix.capacity() : 0;
        return decoder.accept(traceId, bindingId, NO_SCHEMA_ID, data, index + offset, length - offset, next);
    }

    @Override
    public int encode(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Encoder encoder)
    {
        if (prefix != null)
        {
            next.accept(prefix, 0, prefix.capacity());
        }
        int valLength = encoder.accept(traceId, bindingId, schemaId, data, index, length, next);
        int prefixLen = prefix != null ? prefix.capacity() : 0;
        return valLength > 0 ? prefixLen + valLength : -1;
    }

    @Override
    public int encodePadding(
        int length)
    {
        return prefix != null ? prefix.capacity() : 0;
    }

    @Override
    public String location()
    {
        return "http://localhost:8081";
    }
}
