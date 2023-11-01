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
import java.nio.ByteBuffer;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroReadValueValidator extends AvroValueValidator
{
    public AvroReadValueValidator(
        AvroValidatorConfig config,
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
        MutableDirectBuffer value = new UnsafeBuffer();
        int valLength = -1;

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        int schemaId;
        Schema schema;
        if (byteBuf.get() == MAGIC_BYTE)
        {
            schemaId = byteBuf.getInt();
            int size = length - 1 - 4;
            byte[] valBytes = new byte[size];
            data.getBytes(length - size, valBytes);
            schema = fetchSchema(schemaId);
            if (schema != null)
            {
                if ("json".equals(format))
                {
                    byte[] record = serializeAvroRecord(schema, valBytes);
                    value.wrap(record);
                    valLength = record.length;
                }
                else if (validate(schema, valBytes))
                {
                    value.wrap(data);
                    valLength = length;
                }
            }
        }
        else
        {
            schemaId = catalog != null ? catalog.id : 0;
            schema = fetchSchema(schemaId);
            if (schema != null)
            {
                if ("json".equals(format))
                {
                    byte[] record = serializeAvroRecord(schema, payloadBytes);
                    value.wrap(record);
                    valLength = record.length;
                }
                else if (validate(schema, payloadBytes))
                {
                    value.wrap(data);
                    valLength = length;
                }
            }
        }

        next.accept(value, index, valLength);
        return valLength;
    }

    private byte[] serializeAvroRecord(
        Schema schema,
        byte[] payloadBytes)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try
        {
            reader = new GenericDatumReader(schema);
            GenericRecord record = (GenericRecord) reader.read(null,
                    decoder.binaryDecoder(payloadBytes, null));
            JsonEncoder jsonEncoder = encoder.jsonEncoder(record.getSchema(), outputStream);
            writer = record instanceof SpecificRecord ?
                new SpecificDatumWriter<>(record.getSchema()) :
                new GenericDatumWriter<>(record.getSchema());
            writer.write(record, jsonEncoder);
            jsonEncoder.flush();
            outputStream.close();
        }
        catch (Exception e)
        {
        }
        return outputStream.toByteArray();
    }
}
