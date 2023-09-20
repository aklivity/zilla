/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.validator;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.AvroValidatorConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public final class AvroValidator implements Validator
{
    private static final byte MAGIC_BYTE = 0x0;

    private final List<CatalogedConfig> catalogs;
    private final SchemaConfig catalog;
    private final Long2ObjectHashMap<CatalogHandler> handlersById;
    private final CatalogHandler handler;
    private final DecoderFactory decoder;
    private final String subject;
    private DatumReader reader;
    private Parser parser;

    public AvroValidator(
        AvroValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.decoder = DecoderFactory.get();
        this.catalogs = config.catalogs.stream().map(c ->
        {
            c.id = resolveId.applyAsLong(c.name);
            handlersById.put(c.id, supplyCatalog.apply(c.id));
            return c;
        }).collect(Collectors.toList());
        this.handler = handlersById.get(catalogs.get(0).id);
        this.parser = new Schema.Parser();
        this.catalog = catalogs.get(0).schemas.size() != 0 ? catalogs.get(0).schemas.get(0) : null;
        this.subject = config.subject;
    }

    @Override
    public boolean read(
        DirectBuffer data,
        int index,
        int length)
    {
        boolean status = false;
        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        if (byteBuf.get() == MAGIC_BYTE)
        {
            int schemaId = byteBuf.getInt();
            int valLength = length - 1 - 4;
            byte[] valBytes = new byte[valLength];
            data.getBytes(length - valLength, valBytes);

            String schema = handler.resolve(schemaId);

            if (schema != null && validate(schema, valBytes))
            {
                status = true;
            }
        }
        return status;
    }

    @Override
    public boolean write(
        DirectBuffer data,
        int index,
        int length)
    {
        boolean status = false;
        String schema = null;
        int schemaId = catalog != null ? catalog.id : 0;

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);

        if (schemaId > 0)
        {
            schema = handler.resolve(schemaId);
        }
        else if (catalog != null && "topic".equals(catalog.strategy))
        {
            schemaId = handler.resolve(subject, catalog.version);
            if (schemaId > 0)
            {
                schema = handler.resolve(schemaId);
            }
        }

        if (schema != null && validate(schema, payloadBytes))
        {
            status = true;
        }

        return status;
    }

    private boolean validate(
        String schema,
        byte[] payloadBytes)
    {
        boolean status = false;
        try
        {
            reader = new GenericDatumReader(parser.parse(schema));
            reader.read(null, decoder.binaryDecoder(payloadBytes, null));
            status = true;
        }
        catch (Exception e)
        {
        }
        return status;
    }
}
