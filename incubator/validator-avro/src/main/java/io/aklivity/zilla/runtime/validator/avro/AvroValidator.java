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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public final class AvroValidator implements Validator
{
    private static final byte MAGIC_BYTE = 0x0;

    private final List<CatalogedConfig> catalogs;
    private final SchemaConfig catalog;
    private final Long2ObjectHashMap<CatalogHandler> handlersById;
    private final CatalogHandler handler;
    private final DecoderFactory decoder;
    private final EncoderFactory encoder;
    private final String subject;
    private final String expect;
    private DatumReader reader;
    private DatumWriter writer;

    public AvroValidator(
        AvroValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.decoder = DecoderFactory.get();
        this.encoder = EncoderFactory.get();
        this.catalogs = config.catalogs.stream().map(c ->
        {
            c.id = resolveId.applyAsLong(c.name);
            handlersById.put(c.id, supplyCatalog.apply(c.id));
            return c;
        }).collect(Collectors.toList());
        this.handler = handlersById.get(catalogs.get(0).id);
        this.catalog = catalogs.get(0).schemas.size() != 0 ? catalogs.get(0).schemas.get(0) : null;
        this.expect = config.expect;
        this.subject = catalog != null &&
            catalog.subject != null ?
            catalog.subject : config.subject;
    }

    @Override
    public DirectBuffer read(
        DirectBuffer data,
        int index,
        int length)
    {
        MutableDirectBuffer value = new UnsafeBuffer();

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        int schemaId;
        Schema schema;
        if (byteBuf.get() == MAGIC_BYTE)
        {
            schemaId = byteBuf.getInt();
            int valLength = length - 1 - 4;
            byte[] valBytes = new byte[valLength];
            data.getBytes(length - valLength, valBytes);
            schema = fetchSchema(schemaId);
            convertResponse(data, value, valBytes, schema);
        }
        else
        {
            schemaId = catalog != null ? catalog.id : 0;
            schema = fetchSchema(schemaId);
            convertResponse(data, value, payloadBytes, schema);
        }

        return value;
    }

    @Override
    public DirectBuffer write(
        DirectBuffer data,
        int index,
        int length)
    {
        MutableDirectBuffer value = null;

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
                value = buildRequest(record, schemaId);
            }
            else if (validate(schema, payloadBytes))
            {
                value = buildRequest(payloadBytes, schemaId);
            }
        }
        return value;
    }

    private MutableDirectBuffer buildRequest(
        byte[] payloadBytes,
        int schemaId)
    {
        MutableDirectBuffer value = new UnsafeBuffer(new byte[payloadBytes.length + 5]);
        value.putByte(0, MAGIC_BYTE);
        value.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
        value.putBytes(5, payloadBytes);
        return value;
    }

    private void convertResponse(
        DirectBuffer data,
        MutableDirectBuffer value,
        byte[] payloadBytes,
        Schema schema)
    {
        if (schema != null)
        {
            if ("json".equals(expect))
            {
                value.wrap(serializeAvroRecord(schema, payloadBytes));
            }
            else if (validate(schema, payloadBytes))
            {
                value.wrap(data);
            }
        }
    }

    private Schema fetchSchema(
        int schemaId)
    {
        String schema = null;
        if (schemaId > 0)
        {
            schema = handler.resolve(schemaId);
        }
        else if (catalog != null)
        {
            schemaId = handler.resolve(subject, catalog.version);
            if (schemaId > 0)
            {
                schema = handler.resolve(schemaId);
            }
        }
        return schema != null ? new Schema.Parser().parse(schema) : null;
    }

    private boolean validate(
        Schema schema,
        byte[] payloadBytes)
    {
        boolean status = false;
        try
        {
            reader = new GenericDatumReader(schema);
            reader.read(null, decoder.binaryDecoder(payloadBytes, null));
            status = true;
        }
        catch (Exception e)
        {
        }
        return status;
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
