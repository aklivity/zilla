/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.avro.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2ObjectCache;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.json.JsonOverlay;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public abstract class AvroModelHandler
{
    protected static final String VIEW_JSON = "json";

    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();
    private static final int JSON_FIELD_UNION_LENGTH = "\"\":{\"DATA_TYPE\":\"\"},".length();
    private static final int JSON_FIELD_ARRAY_LENGTH = "\"\":[],".length();
    private static final int JSON_FIELD_MAP_LENGTH = "\"\":{},".length();

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final CatalogHandler overlayHandler;
    protected final String overlaySubject;
    protected final String overlayVersion;
    protected final String view;
    protected final AvroModelEventContext event;
    // LENIENT per direction: a semantic-validation failure passes through (inert today — no avro semantic
    // validation stage throws yet, so the wired branch is unreached)
    protected final boolean decodeLenient;
    protected final boolean encodeLenient;

    private final Long2ObjectCache<AvroSchema> schemas;
    private final Int2IntHashMap paddings;
    private final Int2IntHashMap extPaddings;
    private final int paddingMaxItems;

    protected AvroModelHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        CatalogedConfig cataloged = options.cataloged.get(0);
        this.handler = context.supplyCatalog(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.view = options.view;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : options.subject;
        OverlayConfig overlay = catalog != null ? catalog.overlay : null;
        this.overlayHandler = overlay != null ? context.supplyCatalog(overlay.id) : null;
        this.overlaySubject = overlay != null ? overlay.schema.subject : null;
        this.overlayVersion = overlay != null ? overlay.schema.version : null;
        this.decodeLenient = options.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = options.validate.encode == ValidateMode.LENIENT;
        this.schemas = new Long2ObjectCache<>(1, 1024, i -> {});
        this.paddings = new Int2IntHashMap(-1);
        this.extPaddings = new Int2IntHashMap(-1);
        this.event = new AvroModelEventContext(context);
        this.paddingMaxItems = config.paddingMaxItems();
    }

    // avoids computeIfAbsent: a capturing lambda argument is allocated fresh on every call, hit or
    // miss, so this checks the cache directly instead (see ProtobufModelHandler.supplySchema)
    protected final AvroSchema supplySchema(
        int schemaId)
    {
        int overlaySchemaId = overlayHandler != null
            ? overlayHandler.resolve(overlaySubject, overlayVersion)
            : NO_SCHEMA_ID;
        long key = cacheKey(schemaId, overlaySchemaId);
        AvroSchema schema = schemas.get(key);
        if (schema == null)
        {
            schema = resolveSchema(schemaId, overlaySchemaId);
            if (schema != null)
            {
                schemas.put(key, schema);
            }
        }
        return schema;
    }

    private static long cacheKey(
        int schemaId,
        int overlaySchemaId)
    {
        return (long) schemaId << 32 | overlaySchemaId & 0xFFFFFFFFL;
    }

    protected final int supplyPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, id ->
        {
            AvroSchema schema = supplySchema(id);
            return calculatePadding(schema != null ? schema.type() : null);
        });
    }

    protected final int supplyExtPadding(
        int schemaId)
    {
        return extPaddings.computeIfAbsent(schemaId, id -> extPadding(supplySchema(id)));
    }

    // overridden by AvroModelHandlerImpl to sum the padding contributed by each installed model extension;
    // this default (no extensions installed) stays 0
    protected int extPadding(
        AvroSchema schema)
    {
        return 0;
    }

    private AvroSchema resolveSchema(
        int schemaId,
        int overlaySchemaId)
    {
        AvroSchema schema = null;
        String schemaText = handler.resolve(schemaId);
        String overlayText = overlayHandler != null ? overlayHandler.resolve(overlaySchemaId) : null;
        if (schemaText != null && (overlayHandler == null || overlayText != null))
        {
            String resolvedText = overlayText != null ? applyOverlay(schemaText, overlayText) : schemaText;
            schema = Avro.schema(resolvedText);
        }
        return schema;
    }

    private static String applyOverlay(
        String schemaText,
        String overlayText)
    {
        JsonValue document = Json.createReader(new StringReader(schemaText)).readValue();
        JsonValue overlay = Json.createReader(new StringReader(overlayText)).readValue();
        return JsonOverlay.of(overlay).apply(document).toString();
    }

    private int calculatePadding(
        AvroType type)
    {
        int padding = 0;

        if (type != null)
        {
            padding = 10;
            if (type.kind() == AvroKind.RECORD)
            {
                for (io.aklivity.zilla.runtime.common.avro.AvroField field : type.fields())
                {
                    padding += field.name().getBytes(StandardCharsets.UTF_8).length;

                    AvroType fieldType = field.type();
                    switch (fieldType.kind())
                    {
                    case RECORD:
                        padding += calculatePadding(fieldType);
                        break;
                    case UNION:
                        padding += JSON_FIELD_UNION_LENGTH;
                        break;
                    case MAP:
                        padding += JSON_FIELD_MAP_LENGTH + paddingMaxItems + calculatePadding(fieldType.values());
                        break;
                    case ARRAY:
                        padding += JSON_FIELD_ARRAY_LENGTH + paddingMaxItems + calculatePadding(fieldType.items());
                        break;
                    default:
                        padding += JSON_FIELD_STRUCTURE_LENGTH;
                        break;
                    }
                }
            }
        }
        return padding;
    }
}
