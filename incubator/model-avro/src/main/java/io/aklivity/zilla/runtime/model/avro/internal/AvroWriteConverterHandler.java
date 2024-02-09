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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.io.IOException;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroWriteConverterHandler extends AvroModelHandler implements ConverterHandler
{
    public AvroWriteConverterHandler(
        AvroModelConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
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
        int valLength = -1;

        int schemaId = catalog != null && catalog.id > 0
                ? catalog.id
                : handler.resolve(subject, catalog.version);

        if (VIEW_JSON.equals(view))
        {
            valLength = handler.encode(schemaId, data, index, length, next, this::serializeJsonRecord);
        }
        else if (validate(schemaId, data, index, length))
        {
            valLength = handler.encode(schemaId, data, index, length, next, CatalogHandler.Encoder.IDENTITY);
        }
        return valLength;
    }

    private int serializeJsonRecord(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length,
        ValueConsumer next)
    {
        try
        {
            Schema schema = supplySchema(schemaId);
            GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
            GenericDatumWriter<GenericRecord> writer = supplyWriter(schemaId);
            if (reader != null)
            {
                GenericRecord record = supplyRecord(schemaId);
                in.wrap(buffer, index, length);
                expandable.wrap(expandable.buffer());
                record = reader.read(record, decoderFactory.jsonDecoder(schema, in));
                encoderFactory.binaryEncoder(expandable, encoder);
                writer.write(record, encoder);
                encoder.flush();
                next.accept(expandable.buffer(), 0, expandable.position());
            }
        }
        catch (IOException | AvroRuntimeException ex)
        {
            ex.printStackTrace();
        }
        return expandable.position();
    }
}
