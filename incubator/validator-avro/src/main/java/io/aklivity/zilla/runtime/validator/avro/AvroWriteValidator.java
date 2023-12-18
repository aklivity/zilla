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
package io.aklivity.zilla.runtime.validator.avro;

import java.io.IOException;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroWriteValidator extends AvroValidator implements ValueValidator, FragmentValidator
{
    public AvroWriteValidator(
        AvroValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
    }

    @Override
    public int maxPadding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.maxPadding();
    }

    @Override
    public int validate(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validateComplete(data, index, length, next);
    }

    @Override
    public int validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        return (flags & FLAGS_FIN) != 0x00
            ? validateComplete(data, index, length, (b, i, l) -> next.accept(FLAGS_COMPLETE, b, i, l))
            : 0;
    }

    private int validateComplete(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        int schemaId = catalog != null && catalog.id > 0
                ? catalog.id
                : handler.resolve(subject, catalog.version);

        GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);

        if (reader != null)
        {
            if (FORMAT_JSON.equals(format))
            {
                byte[] record = serializeJsonRecord(schemaId, data, index, length);

                int recordLength = record.length;
                if (recordLength > 0)
                {
                    valLength = recordLength + handler.enrich(schemaId, next);
                    valueRO.wrap(record);
                    next.accept(valueRO, 0, recordLength);
                }
            }
            else if (validate(schemaId, data, index, length))
            {
                valLength = length + handler.enrich(schemaId, next);
                next.accept(data, index, length);
            }
        }
        return valLength;
    }

    private byte[] serializeJsonRecord(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        encoded.reset();
        try
        {
            Schema schema = supplySchema(schemaId);
            GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
            GenericDatumWriter<GenericRecord> writer = supplyWriter(schemaId);
            GenericRecord record = supplyRecord(schemaId);
            in.wrap(buffer, index, length);
            record = reader.read(record, decoderFactory.jsonDecoder(schema, in));
            BinaryEncoder out = encoderFactory.binaryEncoder(encoded, null);
            writer.write(record, out);
            out.flush();
            encoded.close();
        }
        catch (IOException | AvroRuntimeException ex)
        {
            ex.printStackTrace();
        }
        return encoded.toByteArray();
    }
}
