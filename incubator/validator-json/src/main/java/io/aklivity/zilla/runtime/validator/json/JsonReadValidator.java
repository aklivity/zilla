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
package io.aklivity.zilla.runtime.validator.json;

import java.nio.ByteBuffer;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public class JsonReadValidator extends JsonValidator
{
    public JsonReadValidator(
        JsonValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, resolveId, supplyCatalog);
    }

    @Override
    public int validate(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        DirectBuffer value = new UnsafeBuffer();
        int valLength = -1;

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        int schemaId;
        String schema;
        if (byteBuf.get() == MAGIC_BYTE)
        {
            schemaId = byteBuf.getInt();
            int size = length - 1 - 4;
            byte[] valBytes = new byte[size];
            data.getBytes(length - size, valBytes);
            schema = fetchSchema(schemaId);
            if (schema != null && validate(schema, valBytes))
            {
                value.wrap(data);
                next.accept(value, index, size);
                valLength = size;
            }
        }
        else
        {
            schemaId = catalog != null &&
                catalog.id > 0 ?
                catalog.id :
                handler.resolve(catalog.subject, catalog.version);
            schema = fetchSchema(schemaId);
            if (schema != null && validate(schema, payloadBytes))
            {
                int size = payloadBytes.length;
                value.wrap(data);
                next.accept(value, index, size);
                valLength = size;
            }
        }
        return valLength;
    }
}
