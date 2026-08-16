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
package io.aklivity.zilla.runtime.model.json.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2ObjectCache;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public abstract class JsonModelHandler
{
    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final CatalogHandler overlayHandler;
    protected final String overlaySubject;
    protected final String overlayVersion;
    protected final JsonModelEventContext event;
    // LENIENT per direction: a schema-validation failure on a structurally valid document passes through
    protected final boolean decodeLenient;
    protected final boolean encodeLenient;

    private final Long2ObjectCache<JsonSchema> schemas;
    private final Int2IntHashMap extPaddings;

    public JsonModelHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.handler = context.supplyCatalog(cataloged.id);
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        OverlayConfig overlay = catalog != null ? catalog.overlay : null;
        this.overlayHandler = overlay != null ? context.supplyCatalog(overlay.id) : null;
        this.overlaySubject = overlay != null ? overlay.schema.subject : null;
        this.overlayVersion = overlay != null ? overlay.schema.version : null;
        this.decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = config.validate.encode == ValidateMode.LENIENT;
        this.schemas = new Long2ObjectCache<>(1, 1024, i -> {});
        this.extPaddings = new Int2IntHashMap(-1);
        this.event = new JsonModelEventContext(context);
    }

    // avoids computeIfAbsent: a capturing lambda argument is allocated fresh on every call, hit or
    // miss, so this checks the cache directly instead (see ProtobufModelHandler.supplySchema)
    protected JsonSchema supplySchema(
        int schemaId)
    {
        int overlaySchemaId = overlayHandler != null
            ? overlayHandler.resolve(overlaySubject, overlayVersion)
            : NO_SCHEMA_ID;
        long key = cacheKey(schemaId, overlaySchemaId);
        JsonSchema schema = schemas.get(key);
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

    protected final int supplyExtPadding(
        int schemaId)
    {
        return extPaddings.computeIfAbsent(schemaId, id -> extPadding(supplySchema(id)));
    }

    // overridden by JsonModelHandlerImpl to sum the padding contributed by each installed model extension
    protected int extPadding(
        JsonSchema schema)
    {
        return 0;
    }

    private static long cacheKey(
        int schemaId,
        int overlaySchemaId)
    {
        return (long) schemaId << 32 | overlaySchemaId & 0xFFFFFFFFL;
    }

    private JsonSchema resolveSchema(
        int schemaId,
        int overlaySchemaId)
    {
        JsonSchema schema = null;
        String schemaText = handler.resolve(schemaId);
        String overlayText = overlayHandler != null ? overlayHandler.resolve(overlaySchemaId) : null;
        if (schemaText != null && (overlayHandler == null || overlayText != null))
        {
            String resolvedText = overlayText != null ? applyOverlay(schemaText, overlayText) : schemaText;
            schema = JsonSchema.of(resolvedText);
        }

        return schema;
    }

    private static String applyOverlay(
        String schemaText,
        String overlayText)
    {
        JsonObject document = Json.createReader(new StringReader(schemaText)).readObject();
        JsonArray patch = Json.createReader(new StringReader(overlayText)).readArray();
        return Json.createPatch(patch).apply(document).toString();
    }
}
