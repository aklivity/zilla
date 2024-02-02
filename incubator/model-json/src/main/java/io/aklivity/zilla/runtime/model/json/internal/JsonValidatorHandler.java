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

import java.util.function.LongFunction;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.io.DirectBufferInputStream;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonValidatorHandler extends JsonModelHandler implements ValidatorHandler
{
    private final DirectBufferInputStream in;
    private final ExpandableDirectByteBuffer buffer;

    private JsonParser parser;
    private int progress;

    public JsonValidatorHandler(
        JsonModelConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
        this.buffer = new ExpandableDirectByteBuffer();
        this.in = new DirectBufferInputStream(buffer);
    }

    @Override
    public boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean status = true;

        try
        {
            if ((flags & FLAGS_INIT) != 0x00)
            {
                this.progress = 0;
            }

            buffer.putBytes(progress, data, index, length);
            progress += length;

            if ((flags & FLAGS_FIN) != 0x00)
            {
                in.wrap(buffer, 0, progress);

                int schemaId = catalog != null && catalog.id > 0
                    ? catalog.id
                    : handler.resolve(subject, catalog.version);

                JsonProvider provider = supplyProvider(schemaId);
                parser = provider.createParser(in);
                while (parser.hasNext())
                {
                    parser.next();
                }
            }
        }
        catch (JsonParsingException ex)
        {
            status = false;
            ex.printStackTrace();
        }

        return status;
    }
}
