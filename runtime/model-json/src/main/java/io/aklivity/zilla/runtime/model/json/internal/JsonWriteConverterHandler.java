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
package io.aklivity.zilla.runtime.model.json.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonWriteConverterHandler extends JsonModelHandler implements ConverterHandler
{
    public JsonWriteConverterHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
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
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        int schemaId = catalog != null && catalog.id > 0
            ? catalog.id
            : handler.resolve(subject, catalog.version);

        if (validate(traceId, bindingId, schemaId, data, index, length))
        {
            valLength = handler.encode(traceId, bindingId, schemaId, data, index, length, next, CatalogHandler.Encoder.IDENTITY);
        }
        return valLength;
    }
}
