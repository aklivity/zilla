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

import org.agrona.collections.Int2ObjectCache;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public abstract class AvroValidator
{
    protected static final byte MAGIC_BYTE = 0x0;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final DecoderFactory decoder;
    protected final EncoderFactory encoder;
    protected final String subject;
    protected final String format;
    protected DatumReader<GenericRecord> reader;
    protected DatumWriter<GenericRecord> writer;

    private final Int2ObjectCache<Schema> cache;

    protected AvroValidator(
        AvroValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.decoder = DecoderFactory.get();
        this.encoder = EncoderFactory.get();
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = supplyCatalog.apply(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.format = config.format;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.cache = new Int2ObjectCache<>(1, 1024, i -> {});
    }

    protected Schema fetchSchema(
        int schemaId)
    {
        Schema schema = null;

        if (schemaId == 0)
        {
            schemaId = handler.resolve(subject, catalog.version);
        }

        if (cache.containsKey(schemaId))
        {
            schema = cache.get(schemaId);
        }
        else
        {
            String schemaStr = handler.resolve(schemaId);
            if (schemaStr != null)
            {
                schema = new Schema.Parser().parse(schemaStr);
                cache.put(schemaId, schema);
            }
        }

        return schema;
    }

    protected boolean validate(
        Schema schema,
        byte[] bytes,
        int offset,
        int length)
    {
        boolean status = false;
        try
        {
            reader = new GenericDatumReader<>(schema);
            reader.read(null, decoder.binaryDecoder(bytes, offset, length, null));
            status = true;
        }
        catch (IOException | AvroRuntimeException e)
        {
        }
        return status;
    }
}
