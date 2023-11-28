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
import java.nio.ByteOrder;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryEncoder;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroWriteValidator extends AvroValidator implements ValueValidator, FragmentValidator
{
    private final MutableDirectBuffer prefixRO = new UnsafeBuffer(new byte[5]);

    public AvroWriteValidator(
        AvroValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
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
        reader = readers.computeIfAbsent(schemaId, this::supplyReader);

        if (reader != null)
        {
            if (FORMAT_JSON.equals(format))
            {
                byte[] record = serializeJsonRecord(schemaId, data, index, length);

                int recordLength = record.length;
                valLength = record.length + 5;

                prefixRO.putByte(0, MAGIC_BYTE);
                prefixRO.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
                next.accept(prefixRO, 0, 5);

                valueRO.wrap(record);
                next.accept(valueRO, 0, recordLength);
            }
            else if (validate(schemaId, data, index, length))
            {
                valLength = length + 5;
                prefixRO.putByte(0, MAGIC_BYTE);
                prefixRO.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);

                next.accept(prefixRO, 0, 5);
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
            Schema schema = schemas.get(schemaId);
            record = records.computeIfAbsent(schemaId, this::supplyRecord);
            in.wrap(buffer, index, length);
            record = reader.read(record, decoderFactory.jsonDecoder(schema, in));
            writer = writers.computeIfAbsent(schemaId, this::supplyWriter);
            BinaryEncoder out = encoderFactory.binaryEncoder(encoded, null);
            writer.write(record, out);
            out.flush();
            encoded.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        return encoded.toByteArray();
    }
}