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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.SCHEMA_REGISTRY;
import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.TEST;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public abstract class AvroValidator
{
    protected static final byte MAGIC_BYTE = 0x0;
    protected static final String FORMAT_JSON = "json";
    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);

    protected final DirectBuffer valueRO;
    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final DecoderFactory decoderFactory;
    protected final EncoderFactory encoderFactory;
    protected final BinaryDecoder decoder;
    protected final String subject;
    protected final String format;
    protected final boolean appendSchemaId;
    protected GenericDatumReader<GenericRecord> reader;
    protected GenericDatumWriter<GenericRecord> writer;
    protected GenericRecord record;
    protected ByteArrayOutputStream encoded;
    protected DirectBufferInputStream in;

    protected final Int2ObjectCache<Schema> schemas;
    protected final Int2ObjectCache<GenericDatumReader<GenericRecord>> readers;
    protected final Int2ObjectCache<GenericDatumWriter<GenericRecord>> writers;
    protected final Int2ObjectCache<GenericRecord> records;
    protected final Int2IntHashMap paddings;

    protected AvroValidator(
        AvroValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.decoderFactory = DecoderFactory.get();
        this.decoder = decoderFactory.binaryDecoder(EMPTY_STREAM, null);
        this.encoderFactory = EncoderFactory.get();
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = supplyCatalog.apply(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.format = config.format;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.readers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.writers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.records = new Int2ObjectCache<>(1, 1024, i -> {});
        this.paddings = new Int2IntHashMap(-1);
        this.valueRO = new UnsafeBuffer();
        this.encoded = new ByteArrayOutputStream();
        this.in = new DirectBufferInputStream();
        this.appendSchemaId = SCHEMA_REGISTRY.equals(handler.type()) || TEST.equals(handler.type());
    }

    private Schema resolveSchema(
        int schemaId)
    {
        Schema schema = null;
        String schemaStr = handler.resolve(schemaId);
        if (schemaStr != null)
        {
            schema = new Schema.Parser().parse(schemaStr);
        }
        return schema;
    }

    private int calculatePadding(
        Schema schema)
    {
        int padding = 2;

        for (Schema.Field field : schema.getFields())
        {
            if (field.schema().getType().equals(Schema.Type.RECORD))
            {
                padding += calculatePadding(field.schema());
            }
            else
            {
                padding += field.name().getBytes().length + 6;
            }
        }

        return padding;
    }

    protected int supplyPadding(
        int schemaId)
    {
        int padding = 0;
        Schema schema = schemas.computeIfAbsent(schemaId, this::resolveSchema);
        if (schema != null)
        {
            padding = calculatePadding(schema);
            paddings.put(schemaId, padding);
        }
        return padding;
    }

    protected GenericDatumReader<GenericRecord> supplyReader(
        int schemaId)
    {
        reader = null;
        Schema schema = schemas.computeIfAbsent(schemaId, this::resolveSchema);
        if (schema != null)
        {
            reader = new GenericDatumReader<GenericRecord>(schema);
            readers.put(schemaId, reader);
        }
        return reader;
    }

    protected GenericDatumWriter<GenericRecord> supplyWriter(
        int schemaId)
    {
        writer = null;
        Schema schema = schemas.computeIfAbsent(schemaId, this::resolveSchema);
        if (schema != null)
        {
            writer = new GenericDatumWriter<GenericRecord>(schema);
            writers.put(schemaId, writer);
        }
        return writer;
    }

    protected GenericRecord supplyRecord(
        int schemaId)
    {
        record = null;
        Schema schema = schemas.computeIfAbsent(schemaId, this::resolveSchema);
        if (schema != null)
        {
            record = new GenericData.Record(schema);
            records.put(schemaId, record);
        }
        return record;
    }

    protected boolean validate(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        try
        {
            record = records.computeIfAbsent(schemaId, this::supplyRecord);
            in.wrap(buffer, index, length);
            reader.read(record, decoderFactory.binaryDecoder(in, decoder));
            status = true;
        }
        catch (IOException | AvroRuntimeException ex)
        {
            ex.printStackTrace();
        }
        return status;
    }
}
