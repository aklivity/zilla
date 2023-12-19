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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.ExpandableDirectBufferOutputStream;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
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

    private static final InputStream EMPTY_INPUT_STREAM = new ByteArrayInputStream(new byte[0]);
    private static final OutputStream EMPTY_OUTPUT_STREAM = new ByteArrayOutputStream(0);
    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final DecoderFactory decoderFactory;
    protected final EncoderFactory encoderFactory;
    protected final BinaryDecoder decoder;
    protected final BinaryEncoder encoder;
    protected final String subject;
    protected final String format;
    protected final ExpandableDirectBufferOutputStream encoded;
    protected final DirectBufferInputStream in;

    private final Int2ObjectCache<Schema> schemas;
    private final Int2ObjectCache<GenericDatumReader<GenericRecord>> readers;
    private final Int2ObjectCache<GenericDatumWriter<GenericRecord>> writers;
    private final Int2ObjectCache<GenericRecord> records;
    private final Int2IntHashMap paddings;

    protected AvroValidator(
        AvroValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.decoderFactory = DecoderFactory.get();
        this.decoder = decoderFactory.binaryDecoder(EMPTY_INPUT_STREAM, null);
        this.encoderFactory = EncoderFactory.get();
        this.encoder = encoderFactory.binaryEncoder(EMPTY_OUTPUT_STREAM, null);
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
        this.encoded = new ExpandableDirectBufferOutputStream(new ExpandableDirectByteBuffer());
        this.in = new DirectBufferInputStream();
    }

    protected final boolean validate(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        try
        {
            GenericRecord record = supplyRecord(schemaId);
            in.wrap(buffer, index, length);
            GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
            reader.read(record, decoderFactory.binaryDecoder(in, decoder));
            status = true;
        }
        catch (IOException | AvroRuntimeException ex)
        {
            ex.printStackTrace();
        }
        return status;
    }

    protected final Schema supplySchema(
        int schemaId)
    {
        return schemas.computeIfAbsent(schemaId, this::resolveSchema);
    }

    protected final int supplyPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, id -> calculatePadding(supplySchema(id)));
    }

    protected final GenericDatumReader<GenericRecord> supplyReader(
        int schemaId)
    {
        return readers.computeIfAbsent(schemaId, this::createReader);
    }

    protected final GenericDatumWriter<GenericRecord> supplyWriter(
        int schemaId)
    {
        return writers.computeIfAbsent(schemaId, this::createWriter);
    }

    protected final GenericRecord supplyRecord(
        int schemaId)
    {
        return records.computeIfAbsent(schemaId, this::createRecord);
    }

    private GenericDatumReader<GenericRecord> createReader(
        int schemaId)
    {
        Schema schema = supplySchema(schemaId);
        GenericDatumReader<GenericRecord> reader = null;
        if (schema != null)
        {
            reader = new GenericDatumReader<GenericRecord>(schema);
        }
        return reader;
    }

    private GenericDatumWriter<GenericRecord> createWriter(
        int schemaId)
    {
        Schema schema = supplySchema(schemaId);
        GenericDatumWriter<GenericRecord> writer = null;
        if (schema != null)
        {
            writer = new GenericDatumWriter<GenericRecord>(schema);
        }
        return writer;
    }

    private GenericRecord createRecord(
        int schemaId)
    {
        Schema schema = supplySchema(schemaId);
        GenericRecord record = null;
        if (schema != null)
        {
            record = new GenericData.Record(schema);
        }
        return record;
    }

    private Schema resolveSchema(
        int schemaId)
    {
        Schema schema = null;
        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            schema = new Schema.Parser().parse(schemaText);
        }
        return schema;
    }

    private int calculatePadding(
        Schema schema)
    {
        int padding = 0;

        if (schema != null)
        {
            padding = 2;
            for (Schema.Field field : schema.getFields())
            {
                if (field.schema().getType().equals(Schema.Type.RECORD))
                {
                    padding += calculatePadding(field.schema());
                }
                else
                {
                    padding += field.name().getBytes().length + JSON_FIELD_STRUCTURE_LENGTH;
                }
            }
        }
        return padding;
    }
}
