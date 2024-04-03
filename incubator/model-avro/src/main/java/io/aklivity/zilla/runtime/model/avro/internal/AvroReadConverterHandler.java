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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.IOException;

import org.agrona.DirectBuffer;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.JsonEncoder;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroReadConverterHandler extends AvroModelHandler implements ConverterHandler
{
    public AvroReadConverterHandler(
        AvroModelConfig config,
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
        int padding = 0;
        if (VIEW_JSON.equals(view))
        {
            int schemaId = handler.resolve(data, index, length);

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
            padding = supplyPadding(schemaId);
        }
        return padding;
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
        return handler.decode(traceId, bindingId, data, index, length, next, this::decodePayload);
    }

    private int decodePayload(
        long traceId,
        long bindingId,
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

        if (VIEW_JSON.equals(view))
        {
            deserializeRecord(traceId, bindingId, schemaId, data, index, length);
            int recordLength = expandable.position();
            if (recordLength > 0)
            {
                next.accept(expandable.buffer(), 0, recordLength);
                valLength = recordLength;
            }
        }
        else if (validate(traceId, bindingId, schemaId, data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }
        return valLength;
    }

    private void deserializeRecord(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        try
        {
            GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
            GenericDatumWriter<GenericRecord> writer = supplyWriter(schemaId);
            if (reader != null)
            {
                GenericRecord record = supplyRecord(schemaId);
                in.wrap(buffer, index, length);
                expandable.wrap(expandable.buffer());
                record = reader.read(record, decoderFactory.binaryDecoder(in, decoder));
                Schema schema = record.getSchema();
                JsonEncoder out = encoderFactory.jsonEncoder(schema, expandable);
                writer.write(record, out);
                out.flush();
            }
        }
        catch (IOException | AvroRuntimeException ex)
        {
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
    }
}
