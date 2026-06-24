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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

// Per-worker factory for a JSON model. One handler serves both directions: supplyDecoder vends a
// per-stream JsonReadModelPipeline (catalog framing stripped, value validated) and supplyEncoder vends a
// per-stream JsonWriteModelPipeline (catalog framing emitted, value validated). Configuration-derived
// state (catalog, schema cache, extraction paths) is shared; in-flight state lives on each pipeline.
public final class JsonModelHandlerImpl extends JsonModelHandler implements ModelHandler
{
    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    public JsonModelHandlerImpl(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelVisitor visitor)
    {
        return new JsonReadModelPipeline(this, visitor);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelVisitor visitor)
    {
        return new JsonWriteModelPipeline(this);
    }

    int decodePadding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
    }

    int encodePadding(
        int length)
    {
        return handler.encodePadding(length);
    }

    int resolveSchemaId(
        DirectBuffer data,
        int index,
        int length)
    {
        int schemaId = handler.resolve(data, index, length);
        if (schemaId == NO_SCHEMA_ID)
        {
            schemaId = catalog.id != NO_SCHEMA_ID
                ? catalog.id
                : handler.resolve(subject, catalog.version);
        }
        return schemaId;
    }

    int resolveSchemaId()
    {
        return catalog != null && catalog.id != NO_SCHEMA_ID
            ? catalog.id
            : handler.resolve(subject, catalog.version);
    }

    // writes the schema framing prefix for the resolved schema id into next, returning the bytes written
    int encodePrefix(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return handler.encode(traceId, bindingId, schemaId, data, index, length, next, NONE_ENCODER);
    }

    JsonPipeline newPipeline(
        int schemaId,
        JsonGeneratorEx generator,
        JsonTransform extractor,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null
            ? JsonEx.stream(JsonEx.createParser())
                .transform(schema.validator())
                .transform(extractor)
                .reporting(reporter)
                .into(generator)
            : null;
    }

    JsonPipeline newPipeline(
        int schemaId,
        JsonGeneratorEx generator,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null
            ? JsonEx.stream(JsonEx.createParser())
                .transform(schema.validator())
                .reporting(reporter)
                .into(generator)
            : null;
    }

    void validationFailure(
        long traceId,
        long bindingId,
        String diagnostic)
    {
        event.validationFailure(traceId, bindingId, diagnostic);
    }
}
