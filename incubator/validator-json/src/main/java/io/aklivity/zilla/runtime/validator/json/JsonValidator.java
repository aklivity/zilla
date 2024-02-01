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
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public class JsonValidator implements Validator
{
    private final JsonProvider schemaProvider;
    private final Long2ObjectHashMap<CatalogHandler> handlersById;
    private final JsonValidationService service;
    private final JsonParserFactory factory;
    private final List<CatalogedConfig> catalogs;
    private final SchemaConfig catalog;
    private final CatalogHandler handler;

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
        this.catalog = catalogs.get(0).schemas.size() != 0 ? catalogs.get(0).schemas.stream().findFirst().get() : null;
        this.handler = handlersById.get(catalogs.get(0).id);
    }

    @Override
    public boolean read(
        DirectBuffer data,
        int index,
        int length)
    {
        return validate(data, index, length);
    }

    @Override
    public boolean write(
        DirectBuffer data,
        int index,
        int length)
    {
        return validate(data, index, length);
    }

    private boolean validate(
        DirectBuffer data,
        int index,
        int length)
    {
        String schema = null;
        int schemaId = catalog != null ? catalog.id : 0;

        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);

        if (schemaId > 0)
        {
            schema = handler.resolve(schemaId);
        }
        else if (catalog != null)
        {
            schemaId = handler.resolve(catalog.subject, catalog.version);
            if (schemaId != 0)
            {
                schema = handler.resolve(schemaId);
            }
        }

        return schema != null && validate(schema, payloadBytes);
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
            provider.createReader(input).readValue();
            status = true;
        }
        catch (Exception e)
        {
        }
        return status;
    }
}
