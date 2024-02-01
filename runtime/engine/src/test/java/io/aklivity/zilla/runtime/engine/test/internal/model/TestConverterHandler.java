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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class TestConverterHandler implements ConverterHandler
{
    private final int length;
    private final int schemaId;
    private final boolean read;
    private final CatalogHandler handler;
    private final SchemaConfig schema;

    public TestConverterHandler(
        TestModelConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.length = config.length;
        this.read = config.read;
        CatalogedConfig cataloged = config.cataloged != null && !config.cataloged.isEmpty()
            ? config.cataloged.get(0)
            : null;
        schema = cataloged != null ? cataloged.schemas.stream().findFirst().get() : null;
        schemaId = schema != null ? schema.id : 0;
        this.handler = cataloged != null ? supplyCatalog.apply(cataloged.id) : null;
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.encodePadding();
    }

    @Override
    public int convert(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean valid = length == this.length;
        if (valid)
        {
            next.accept(data, index, length);
        }
        return valid ? length : -1;
    }
}

