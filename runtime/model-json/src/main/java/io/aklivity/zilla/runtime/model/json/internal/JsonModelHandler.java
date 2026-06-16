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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonValidationException;
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
    private static final int PROJECT_HEADROOM = 64;
    private static final int PROJECT_CAPACITY_MIN = 1024;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final JsonModelEventContext event;
    protected final Map<String, OctetsFW> extracted;

    protected final JsonProvider schemaProvider;
    protected final MutableDirectBuffer projectBuffer;

    private final Int2ObjectCache<JsonSchema> schemas;
    private final Map<JsonSchema, JsonPipeline> projectors;
    private final JsonGeneratorEx generator;
    private final DirectBufferInputStream in;

    private byte[] projectBytes;
    private JsonParser parser;

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
        this.projectors = new IdentityHashMap<>();
        this.generator = JsonEx.createGenerator();
        this.projectBytes = new byte[1024];
        this.projectBuffer = new UnsafeBuffer(projectBytes);
        this.in = new DirectBufferInputStream();
        this.event = new JsonModelEventContext(context);
        this.extracted = new HashMap<>();
    }

    protected final int project(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        int projected = -1;

        JsonSchema schema = supplySchema(schemaId);
        if (schema != null)
        {
            int capacity = Math.max(PROJECT_CAPACITY_MIN, length + PROJECT_HEADROOM);
            if (projectBytes.length < capacity)
            {
                projectBytes = new byte[capacity];
                projectBuffer.wrap(projectBytes);
            }

            JsonPipeline pipeline = projectors.computeIfAbsent(schema, this::newProjector);
            generator.wrap(projectBuffer, 0, projectBuffer.capacity());
            pipeline.reset();

            JsonPipeline.Status status = pipeline.feed(buffer, index, length);
            if (status == JsonPipeline.Status.COMPLETED)
            {
                projected = generator.length();
            }
            else
            {
                diagnose(traceId, bindingId, schema, buffer, index, length);
            }
        }

        return projected;
    }

    protected final void extract(
        DirectBuffer buffer,
        int index,
        int length)
    {
        for (OctetsFW value : extracted.values())
        {
            value.wrap(EMPTY_BUFFER, 0, 0);
        }

        in.wrap(buffer, index, length);
        parser = schemaProvider.createParser(in);
        OctetsFW valueBytes = null;
        while (parser.hasNext())
        {
            JsonParser.Event next = parser.next();
            switch (next)
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
                    valueBytes.wrap(buffer, offset - valLength, offset);
                    valueBytes = null;
                }
                break;
            case VALUE_NUMBER:
                if (valueBytes != null)
                {
                    int offset = (int) parser.getLocation().getStreamOffset();
                    offset += index;
                    int valLength = calculateValueLength();
                    valueBytes.wrap(buffer, offset - valLength, offset);
                    valueBytes = null;
                }
                break;
            default:
                break;
            }
        }
    }

    protected JsonSchema supplySchema(
        int schemaId)
    {
        return schemas.computeIfAbsent(schemaId, this::resolveSchema);
    }

    private JsonPipeline newProjector(
        JsonSchema schema)
    {
        return JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(JsonEx.projector(schema))
            .into(JsonEx.createSink(generator));
    }

    private void diagnose(
        long traceId,
        long bindingId,
        JsonSchema schema,
        DirectBuffer buffer,
        int index,
        int length)
    {
        try
        {
            in.wrap(buffer, index, length);
            JsonParser diagnostic = schema.newParser(true, schemaProvider.createParser(in));
            while (diagnostic.hasNext())
            {
                diagnostic.next();
            }
        }
        catch (JsonValidationException ex)
        {
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
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
