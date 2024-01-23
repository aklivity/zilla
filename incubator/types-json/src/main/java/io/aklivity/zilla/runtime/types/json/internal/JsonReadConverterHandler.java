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
package io.aklivity.zilla.runtime.types.json.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.converter.ConverterHandler;
import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;
import io.aklivity.zilla.runtime.types.json.config.JsonConverterConfig;

public class JsonReadConverterHandler extends JsonConverterHandler implements ConverterHandler
{
    public JsonReadConverterHandler(
        JsonConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
    }

    @Override
    public int convert(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return handler.decode(data, index, length, next, this::decodePayload);
    }

    private int decodePayload(
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        if (schemaId == NO_SCHEMA_ID)
        {
            if (catalog.id != NO_SCHEMA_ID)
            {
                schemaId = catalog.id;
            }
            else
            {
                schemaId = handler.resolve(subject, catalog.version);
            }
        }

        if (validate(schemaId, data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }
        return valLength;
    }
}
