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

public abstract class JsonValidator implements Validator
{
    static final byte MAGIC_BYTE = 0x0;

    final JsonProvider schemaProvider;
    final Long2ObjectHashMap<CatalogHandler> handlersById;
    final JsonValidationService service;
    final JsonParserFactory factory;
    final List<CatalogedConfig> catalogs;
    final SchemaConfig catalog;
    final CatalogHandler handler;
    final String subject;

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

    String fetchSchema(
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

    boolean validate(
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
