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
import static java.util.Objects.requireNonNull;

import java.util.List;

import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonStream;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtContext;

// Per-worker factory for a JSON model. One handler serves both directions: supplyDecoder vends a
// per-stream JsonModelDecoderPipeline (catalog framing stripped, value validated) and supplyEncoder vends a
// per-stream JsonModelEncoderPipeline (catalog framing emitted, value validated). Configuration-derived
// state (catalog, schema cache, extraction paths) is shared; in-flight state lives on each pipeline.
public final class JsonModelHandlerImpl extends JsonModelHandler implements ModelHandler
{
    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    private final JsonModelConfig options;
    private final List<JsonModelExtContext> exts;

    public JsonModelHandlerImpl(
        JsonModelConfig config,
        EngineContext context,
        List<JsonModelExtContext> exts)
    {
        super(config, context);
        this.options = config;
        this.exts = exts;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelTransform transform)
    {
        return new JsonModelDecoderPipeline(this, requireNonNull(transform));
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelTransform transform)
    {
        return new JsonModelEncoderPipeline(this);
    }

    int decodePadding(
        DirectBufferEx data,
        int index,
        int length)
    {
        int schemaId = resolveSchemaId(data, index, length);
        return handler.decodePadding(data, index, length) + supplyExtPadding(schemaId);
    }

    @Override
    protected int extPadding(
        JsonSchema schema)
    {
        int padding = 0;
        if (schema != null)
        {
            for (JsonModelExtContext ext : exts)
            {
                padding += ext.supplyHandler(schema, options).padding(schema);
            }
        }
        return padding;
    }

    int encodePadding(
        int length)
    {
        return handler.encodePadding(length);
    }

    int resolveSchemaId(
        DirectBufferEx data,
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
        DirectBufferEx data,
        int index,
        int length,
        ValueConsumer next)
    {
        return handler.encode(traceId, bindingId, schemaId, data, index, length, next, NONE_ENCODER);
    }

    // the decode path: extractor is null when the caller's ModelTransform is NONE (the observation-only
    // extraction stage is skipped), but this overload is always the decode path regardless, so installed
    // extensions always fold in here
    JsonPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx generator,
        JsonTransform extractor,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        JsonStream stream = schema != null
            ? extend(JsonEx.stream(JsonEx.createParser()), schema).transform(schema.validator(lenient))
            : null;
        return stream != null
            ? (extractor != null ? stream.transform(extractor) : stream)
                .lenient(lenient)
                .reporting(reporter)
                .into(generator)
            : null;
    }

    // the encode path: the write path into the broker, which must preserve the full, undisclosed value, so
    // installed extensions (decode-only) never fold in here
    JsonPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx generator,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null
            ? JsonEx.stream(JsonEx.createParser())
                .transform(schema.validator(lenient))
                .lenient(lenient)
                .reporting(reporter)
                .into(generator)
            : null;
    }

    // folds every installed json model extension's own stage(s) into the decoder stream, in discovery
    // order, ahead of this handler's own validator/extractor stages; decode is the read path out of the
    // broker, where a read-side concern like disclosure redaction belongs. encode never calls this: it is
    // the write path into the broker and must preserve the full, undisclosed value.
    private JsonStream extend(
        JsonStream stream,
        JsonSchema schema)
    {
        JsonStream extended = stream;
        for (JsonModelExtContext ext : exts)
        {
            extended = (JsonStream) ext.supplyHandler(schema, options).transform(extended);
        }
        return extended;
    }

    void validationFailure(
        long traceId,
        long bindingId,
        String diagnostic)
    {
        event.validationFailure(traceId, bindingId, diagnostic);
    }
}
