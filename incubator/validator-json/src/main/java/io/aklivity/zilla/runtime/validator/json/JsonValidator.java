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
package io.aklivity.zilla.runtime.validator.json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.function.ToIntValueFunction;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public class JsonValidator implements Validator
{
    private static final byte MAGIC_BYTE = 0x0;

    private final JsonProvider schemaProvider;
    private final Long2ObjectHashMap<CatalogHandler> handlersById;
    private final JsonValidationService service;
    private final JsonParserFactory factory;
    private final List<CatalogedConfig> catalogs;
    private final SchemaConfig catalog;
    private final CatalogHandler handler;
    private final String subject;

    public JsonValidator(
        JsonValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.schemaProvider = JsonProvider.provider();
        this.service = JsonValidationService.newInstance();
        this.factory = schemaProvider.createParserFactory(null);
        this.catalogs = config.catalogs.stream().map(c ->
        {
            c.id = resolveId.applyAsLong(c.name);
            handlersById.put(c.id, supplyCatalog.apply(c.id));
            return c;
        }).collect(Collectors.toList());
        this.catalog = catalogs.get(0).schemas.size() != 0 ? catalogs.get(0).schemas.get(0) : null;
        this.handler = handlersById.get(catalogs.get(0).id);
        this.subject = catalog != null &&
            catalog.subject != null ?
            catalog.subject : config.subject;
    }

    @Override
    public int read(
        DirectBuffer data,
        int index,
        int length,
        ToIntValueFunction next)
    {
        DirectBuffer value = new UnsafeBuffer();

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        int schemaId;
        String schema;
        if (byteBuf.get() == MAGIC_BYTE)
        {
            schemaId = byteBuf.getInt();
            int valLength = length - 1 - 4;
            byte[] valBytes = new byte[valLength];
            data.getBytes(length - valLength, valBytes);
            schema = fetchSchema(schemaId);
            if (schema != null && validate(schema, valBytes))
            {
                value.wrap(data);
            }
        }
        else
        {
            schemaId = catalog != null &&
                catalog.id > 0 ?
                catalog.id :
                handler.resolve(catalog.subject, catalog.version);
            schema = fetchSchema(schemaId);
            if (schema != null && validate(schema, payloadBytes))
            {
                value.wrap(data);
            }
        }
        return value.capacity() == 0 ? -1 : next.applyAsInt(value, index, value.capacity());
    }

    @Override
    public int write(
        DirectBuffer data,
        int index,
        int length,
        ToIntValueFunction next)
    {
        MutableDirectBuffer value = null;

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);

        int schemaId = catalog != null &&
            catalog.id > 0 ?
            catalog.id :
            handler.resolve(catalog.subject, catalog.version);
        String schema = fetchSchema(schemaId);

        if (schema != null && validate(schema, payloadBytes))
        {
            value = new UnsafeBuffer(new byte[data.capacity() + 5]);
            value.putByte(0, MAGIC_BYTE);
            value.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
            value.putBytes(5, payloadBytes);
        }
        return value == null ||
            value.capacity() == 0 ? -1 : next.applyAsInt(value, index, value.capacity());
    }

    private String fetchSchema(
        int schemaId)
    {
        String schema = null;
        if (schemaId > 0)
        {
            schema = handler.resolve(schemaId);
        }
        else if (catalog != null)
        {
            if (schemaId > 0)
            {
                schema = handler.resolve(schemaId);
            }
        }
        return schema;
    }

    private boolean validate(
        String schema,
        byte[] payloadBytes)
    {
        boolean status = false;
        try
        {
            JsonParser schemaParser = factory.createParser(new StringReader(schema));
            JsonSchemaReader reader = service.createSchemaReader(schemaParser);
            JsonSchema jsonSchema = reader.read();
            JsonProvider provider = service.createJsonProvider(jsonSchema, parser -> ProblemHandler.throwing());
            InputStream input = new ByteArrayInputStream(payloadBytes);
            provider.createReader(input).readObject();
            status = true;
        }
        catch (Exception e)
        {
        }
        return status;
    }
}
