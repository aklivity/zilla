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

import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public abstract class JsonModelHandler
{
    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final JsonModelEventContext event;
    // LENIENT per direction: a schema-validation failure on a structurally valid document passes through
    protected final boolean decodeLenient;
    protected final boolean encodeLenient;

    private final Int2ObjectCache<JsonSchema> schemas;

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
        this.decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = config.validate.encode == ValidateMode.LENIENT;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.event = new JsonModelEventContext(context);
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
