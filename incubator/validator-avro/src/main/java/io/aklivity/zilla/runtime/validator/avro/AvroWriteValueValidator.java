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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroWriteValueValidator extends AvroValueValidator
{
    public AvroWriteValueValidator(
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
        MutableDirectBuffer value = null;
        int valLength = -1;

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);

        int schemaId = catalog != null &&
                catalog.id > 0 ?
                catalog.id :
                handler.resolve(catalog.subject, catalog.version);
        Schema schema = fetchSchema(schemaId);

        if (schema != null)
        {
            if ("json".equals(expect))
            {
                byte[] record = serializeJsonRecord(schema, payloadBytes);
                value = new UnsafeBuffer(new byte[record.length + 5]);
                value.putByte(0, MAGIC_BYTE);
                value.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
                value.putBytes(5, record);
                valLength = record.length + 5;
            }
            else if (validate(schema, payloadBytes))
            {
                value = new UnsafeBuffer(new byte[payloadBytes.length + 5]);
                value.putByte(0, MAGIC_BYTE);
                value.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
                value.putBytes(5, payloadBytes);
                valLength = length + 5;
            }
            next.accept(value, index, valLength);
        }
        return valLength;
    }

    private byte[] serializeJsonRecord(
        Schema schema,
        byte[] payloadBytes)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try
        {
            reader = new GenericDatumReader(schema);
            GenericRecord genericRecord = new GenericData.Record(schema);
            GenericRecord record = (GenericRecord) reader.read(genericRecord,
                    decoder.jsonDecoder(schema, new ByteArrayInputStream(payloadBytes)));
            writer = new GenericDatumWriter<>(schema);
            BinaryEncoder binaryEncoder = encoder.binaryEncoder(outputStream, null);
            writer.write(record, binaryEncoder);
            binaryEncoder.flush();
            outputStream.close();
        }
        catch (Exception e)
        {
        }
        return outputStream.toByteArray();
    }
}
