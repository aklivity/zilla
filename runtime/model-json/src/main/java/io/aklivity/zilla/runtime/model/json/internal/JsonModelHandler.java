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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
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

    protected final JsonProvider schemaProvider;

    private final Int2ObjectCache<JsonSchema> schemas;

    private JsonParser parser;
    private DirectBufferInputStream in;

    public JsonModelHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        this.schemaProvider = JsonProvider.provider();
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.handler = context.supplyCatalog(cataloged.id);
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
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
            JsonSchema schema = supplySchema(schemaId);
            status &= schema != null;
            if (status)
            {
                for (OctetsFW value: extracted.values())
                {
                    value.wrap(EMPTY_BUFFER, 0, 0);
                }

                // common-json validates by consuming a parser; a second pass extracts field
                // offsets. Restore single-pass once common-json offers a validating parser.
                in.wrap(buffer, index, length);
                boolean valid;
                try (JsonParser validator = schemaProvider.createParser(in))
                {
                    valid = schema.validate(validator);
                }
                status &= valid;

                if (!valid)
                {
                    event.validationFailure(traceId, bindingId, failure(schema, buffer, index, length));
                }
                else if (!extracted.isEmpty())
                {
                    extract(buffer, index, length);
                }
            }
        }
        catch (RuntimeException ex)
        {
            status = false;
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
        return status;
    }

    private void extract(
        DirectBuffer buffer,
        int index,
        int length)
    {
        in.wrap(buffer, index, length);
        parser = schemaProvider.createParser(in);
        OctetsFW valueBytes = null;
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME:
                String key = parser.getString();
                valueBytes = extracted.get(key);
                break;
            case VALUE_STRING:
                if (valueBytes != null)
                {
                    int offset = (int) parser.getLocation().getStreamOffset() - DOUBLE_QUOTE_LENGTH + index;
                    int valLength = calculateValueLength();
                    valueBytes.wrap(in.buffer(), offset - valLength, offset);
                    valueBytes = null;
                }
                break;
            case VALUE_NUMBER:
                if (valueBytes != null)
                {
                    int offset = (int) parser.getLocation().getStreamOffset() + index;
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

    private String failure(
        JsonSchema schema,
        DirectBuffer buffer,
        int index,
        int length)
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        in.wrap(buffer, index, length);
        try (JsonParser validator = schemaProvider.createParser(in))
        {
            schema.validate(validator, diagnostics::add);
        }
        return diagnostics.isEmpty() ? "json validation failed" : diagnostics.get(0).toString();
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

    protected JsonSchema supplySchema(
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
            schema = JsonSchema.of(schemaText);
        }

        return schema;
    }
}
