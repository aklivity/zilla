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
import org.apache.avro.io.JsonEncoder;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroReadValidator extends AvroValidator implements ValueValidator, FragmentValidator
{
    public AvroReadValidator(
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
        int padding = 0;
        if (FORMAT_JSON.equals(format))
        {
            padding = supplyPadding(handler.resolve(data, index, length, catalog, subject));
        }
        return padding;
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
        return handler.decode(data, index, length, next, catalog, subject, this::validatePayload);
    }

    private int validatePayload(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        int schemaId)
    {
        int valLength = -1;
        if (FORMAT_JSON.equals(format))
        {
            deserializeRecord(schemaId, data, index, length);
            int recordLength = encoded.position();
            if (recordLength > 0)
            {
                next.accept(encoded.buffer(), 0, recordLength);
                valLength = recordLength;
            }
        }
        else if (validate(schemaId, data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }
        return valLength;
    }

    private void deserializeRecord(
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
                encoded.wrap(encoded.buffer());
                record = reader.read(record, decoderFactory.binaryDecoder(in, decoder));
                Schema schema = record.getSchema();
                JsonEncoder out = encoderFactory.jsonEncoder(schema, encoded);
                writer.write(record, out);
                out.flush();
            }
        }
        catch (IOException | AvroRuntimeException ex)
        {
            ex.printStackTrace();
        }
    }
}
