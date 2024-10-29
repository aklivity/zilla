/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.json.internal;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidatingException;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.model.json.internal.types.OctetsFW;

public abstract class JsonModelHandler
{
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final int DOUBLE_QUOTE_LENGTH = 1;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final JsonModelEventContext event;
    protected final Map<String, OctetsFW> extracted;

    private final Int2ObjectCache<JsonSchema> schemas;
    private final Int2ObjectCache<JsonProvider> providers;
    private final JsonProvider schemaProvider;
    private final JsonValidationService service;
    private final JsonParserFactory factory;

    private JsonParser parser;
    private DirectBufferInputStream in;

    public JsonModelHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        this.schemaProvider = JsonProvider.provider();
        this.service = JsonValidationService.newInstance();
        this.factory = schemaProvider.createParserFactory(null);
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.handler = context.supplyCatalog(cataloged.id);
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.providers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.in = new DirectBufferInputStream();
        this.event = new JsonModelEventContext(context);
        this.extracted = new HashMap<>();
    }

    protected final boolean validate(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = true;
        try
        {
            JsonProvider provider = supplyProvider(schemaId);
            status &= provider != null;
            if (status)
            {
                for (OctetsFW value: extracted.values())
                {
                    value.wrap(EMPTY_BUFFER, 0, 0);
                }
                in.wrap(buffer, index, length);
                parser = provider.createParser(in);
                OctetsFW valueBytes = null;
                while (parser.hasNext())
                {
                    JsonParser.Event event = parser.next();
                    if (!extracted.isEmpty())
                    {
                        switch (event)
                        {
                        case KEY_NAME:
                            String key = parser.getString();
                            valueBytes = extracted.get(key);
                            break;
                        case VALUE_STRING:
                            if (valueBytes != null)
                            {
                                int offset = (int) parser.getLocation().getStreamOffset() - DOUBLE_QUOTE_LENGTH;
                                offset += index;
                                int valLength = calculateValueLength();
                                valueBytes.wrap(in.buffer(), offset - valLength, offset);
                                valueBytes = null;
                            }
                            break;
                        case VALUE_NUMBER:
                            if (valueBytes != null)
                            {
                                int offset = (int) parser.getLocation().getStreamOffset();
                                offset += index;
                                int valLength = calculateValueLength();
                                valueBytes.wrap(in.buffer(), offset - valLength, offset);
                                valueBytes = null;
                            }
                            break;
                        default:
                            break;
                        }
                    }
                }
            }
        }
        catch (JsonValidatingException ex)
        {
            status = false;
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
        return status;
    }

    private int calculateValueLength()
    {
        int length = 0;
        String value = parser.getString();
        int valLength = value.length();
        for (int i = 0; i < valLength; i++)
        {
            char c = value.charAt(i);
            if ((c & 0xFF80) == 0)
            {
                length += 1;
            }
            else if ((c & 0xF800) == 0)
            {
                length += 2;
            }
            else if (Character.isHighSurrogate(c))
            {
                length += 4;
                i++;
            }
            else
            {
                length += 3;
            }
        }
        return length;
    }

    protected JsonProvider supplyProvider(
        int schemaId)
    {
        return providers.computeIfAbsent(schemaId, this::createProvider);
    }

    private JsonSchema supplySchema(
        int schemaId)
    {
        return schemas.computeIfAbsent(schemaId, this::resolveSchema);
    }

    private JsonSchema resolveSchema(
        int schemaId)
    {
        JsonSchema schema = null;
        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            JsonParser schemaParser = factory.createParser(new StringReader(schemaText));
            JsonSchemaReader reader = service.createSchemaReader(schemaParser);
            schema = reader.read();
        }

        return schema;
    }

    private JsonProvider createProvider(
        int schemaId)
    {
        JsonSchema schema = supplySchema(schemaId);
        JsonProvider provider = null;
        if (schema != null)
        {
            provider = service.createJsonProvider(schema, parser -> ProblemHandler.throwing());
        }
        return provider;
    }
}
