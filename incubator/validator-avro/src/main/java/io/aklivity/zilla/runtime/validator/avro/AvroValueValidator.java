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

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.collections.Long2ObjectHashMap;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public abstract class AvroValueValidator implements ValueValidator
{
    static final byte MAGIC_BYTE = 0x0;

    final List<CatalogedConfig> catalogs;
    final SchemaConfig catalog;
    final Long2ObjectHashMap<CatalogHandler> handlersById;
    final CatalogHandler handler;
    final DecoderFactory decoder;
    final EncoderFactory encoder;
    final String subject;
    final String expect;
    DatumReader reader;
    DatumWriter writer;

    public AvroValueValidator(
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

    Schema fetchSchema(
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

    boolean validate(
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
}
