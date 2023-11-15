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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.LongFunction;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroReadValidator extends AvroValidator implements ValueValidator, FragmentValidator
{
    private final DirectBuffer valueRO = new UnsafeBuffer();

    public AvroReadValidator(
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

        byte[] payloadBytes = new byte[length];
        data.getBytes(index, payloadBytes);

        int schemaId;
        int progress = 0;
        if (data.getByte(index) == MAGIC_BYTE)
        {
            progress += BitUtil.SIZE_OF_BYTE;
            schemaId = data.getInt(index + progress);
            progress += BitUtil.SIZE_OF_INT;
        }
        else
        {
            schemaId = catalog != null ? catalog.id : 0;
        }

        Schema schema = fetchSchema(schemaId);
        if (schema != null)
        {
            if ("json".equals(format))
            {
                byte[] record = deserializeAvroRecord(schema, payloadBytes, progress, length - progress);
                valueRO.wrap(record);
                valLength = record.length;
            }
            else if (validate(schema, payloadBytes, progress, length - progress))
            {
                valueRO.wrap(data);
                valLength = length;
            }
            if (valLength != -1)
            {
                next.accept(valueRO, 0, valLength);
            }
        }
        return valLength;
    }

    private byte[] deserializeAvroRecord(
        Schema schema,
        byte[] bytes,
        int offset,
        int length)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try
        {
            reader = new GenericDatumReader(schema);
            GenericRecord record = (GenericRecord) reader.read(null,
                decoder.binaryDecoder(bytes, offset, length, null));
            JsonEncoder jsonEncoder = encoder.jsonEncoder(record.getSchema(), outputStream);
            writer = record instanceof SpecificRecord ?
                new SpecificDatumWriter<>(record.getSchema()) :
                new GenericDatumWriter<>(record.getSchema());
            writer.write(record, jsonEncoder);
            jsonEncoder.flush();
            outputStream.close();
        }
        catch (IOException e)
        {
        }
        return outputStream.toByteArray();
    }
}
