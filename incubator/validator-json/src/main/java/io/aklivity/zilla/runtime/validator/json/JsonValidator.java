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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.SCHEMA_REGISTRY;
import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.TEST;

import java.io.StringReader;
import java.util.function.LongFunction;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.io.DirectBufferInputStream;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidatingException;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public abstract class JsonValidator
{
    protected static final byte MAGIC_BYTE = 0x0;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final boolean appendSchemaId;
    protected final String subject;

    private final Int2ObjectCache<JsonSchema> schemas;
    private final Int2ObjectCache<JsonProvider> providers;
    private final JsonProvider schemaProvider;
    private final JsonValidationService service;
    private final JsonParserFactory factory;
    private JsonProvider provider;
    private DirectBufferInputStream in;

    public JsonValidator(
        JsonValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.schemaProvider = JsonProvider.provider();
        this.service = JsonValidationService.newInstance();
        this.factory = schemaProvider.createParserFactory(null);
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.handler = supplyCatalog.apply(cataloged.id);
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.providers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.in = new DirectBufferInputStream();
        this.appendSchemaId = SCHEMA_REGISTRY.equals(handler.type()) || TEST.equals(handler.type());
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
            provider = providers.computeIfAbsent(schemaId, this::supplyProvider);
            in.wrap(buffer, index, length);
            provider.createReader(in).readObject();
            status = true;
        }
        catch (JsonValidatingException ex)
        {
            ex.printStackTrace();
        }
        return status;
    }

    private JsonSchema resolveSchema(
        int schemaId)
    {
        JsonSchema schema = null;

        String schemaStr = handler.resolve(schemaId);
        if (schemaStr != null)
        {
            JsonParser schemaParser = factory.createParser(new StringReader(schemaStr));
            JsonSchemaReader reader = service.createSchemaReader(schemaParser);
            schema = reader.read();
            schemas.put(schemaId, schema);
        }

        return schema;
    }

    private JsonProvider supplyProvider(
        int schemaId)
    {
        JsonSchema schema = schemas.computeIfAbsent(schemaId, this::resolveSchema);
        if (schema != null)
        {
            provider = service.createJsonProvider(schema, parser -> ProblemHandler.throwing());
            providers.put(schemaId, provider);
        }
        return provider;
    }
}
