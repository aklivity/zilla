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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.function.LongFunction;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
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
            int schemaId;
            if (data.getByte(index) == MAGIC_BYTE)
            {
                schemaId = data.getInt(index + BitUtil.SIZE_OF_BYTE, ByteOrder.BIG_ENDIAN);
            }
            else if (catalog.id != NO_SCHEMA_ID)
            {
                schemaId = catalog.id;
            }
            else
            {
                schemaId = handler.resolve(subject, catalog.version);
            }
            padding = paddings.computeIfAbsent(schemaId, this::supplyPadding);
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
        int valLength = -1;

        int schemaId;
        int progress = 0;
        if (data.getByte(index) == MAGIC_BYTE)
        {
            progress += BitUtil.SIZE_OF_BYTE;
            schemaId = data.getInt(index + progress, ByteOrder.BIG_ENDIAN);
            progress += BitUtil.SIZE_OF_INT;
        }
        else if (catalog.id != NO_SCHEMA_ID)
        {
            schemaId = catalog.id;
        }
        else
        {
            schemaId = handler.resolve(subject, catalog.version);
        }

        reader = readers.computeIfAbsent(schemaId, this::supplyReader);
        if (reader != null)
        {
            int payloadLength = length - progress;
            int payloadIndex = index + progress;
            if (FORMAT_JSON.equals(format))
            {
                byte[] record = deserializeRecord(schemaId, data, payloadIndex, payloadLength);
                int recordLength = record.length;
                if (recordLength > 0)
                {
                    valLength = record.length;
                    valueRO.wrap(record);
                    next.accept(valueRO, 0, valLength);
                }
            }
            else if (validate(schemaId, data, payloadIndex, payloadLength))
            {
                next.accept(data, payloadIndex, payloadLength);
                valLength = payloadLength;
            }
        }
        return valLength;
    }

    private byte[] deserializeRecord(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        encoded.reset();
        try
        {
            record = records.computeIfAbsent(schemaId, this::supplyRecord);
            in.wrap(buffer, index, length);
            record = reader.read(record, decoderFactory.binaryDecoder(in, decoder));
            Schema schema = record.getSchema();
            JsonEncoder out = encoderFactory.jsonEncoder(schema, encoded);
            writer = writers.computeIfAbsent(schemaId, this::supplyWriter);
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
