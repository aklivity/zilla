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
import java.util.Map;

import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
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

    // forces canonical re-rendering on the decode pipeline once an extension is installed -- see
    // newPipeline's own note on why byte-preserving delivery is unsafe once a value can be substituted
    private static final Map<String, Object> STRUCTURED_DELIVERY = Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED);

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

    // the catalog's own embedded-framing byte count (e.g. a magic-byte-plus-id header some catalogs embed
    // ahead of the value) -- deliberately excludes any installed extension's padding contribution, unlike
    // decodePadding above: that count sizes the destination for a value an extension may expand, this one
    // sizes how many source bytes to skip before the value itself begins, and the two are never the same
    // quantity once an extension contributes non-zero padding
    int prefix(
        DirectBufferEx data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
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
        return handler.encodePadding(length) + supplyExtPadding(resolveSchemaId());
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
    // extensions always fold in here. An installed extension may substitute a value (redacting a field,
    // say), which has no original source bytes to splice, so decode forces structured (canonical) delivery
    // whenever at least one extension is folded in -- byte-preserving delivery remains the default with
    // none installed, matching the pre-extension behavior exactly
    JsonPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx generator,
        JsonTransform extractor,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        JsonStream stream = schema != null
            ? extendDecode(JsonEx.stream(JsonEx.createParser()), schema).transform(schema.validator(lenient))
            : null;
        JsonStream terminal = stream != null
            ? (extractor != null ? stream.transform(extractor) : stream)
                .lenient(lenient)
                .reporting(reporter)
            : null;
        return terminal != null
            ? exts.isEmpty()
                ? terminal.into(generator)
                : terminal.into(generator, STRUCTURED_DELIVERY)
            : null;
    }

    // the encode path: the write path into the broker. A caller's value being encoded into its canonical
    // form is extended independently of the decode path above, so an extension that only redacts on read
    // (the default encode()) leaves this path unchanged; one that also needs to apply on write overrides
    // encode() to fold in here.
    JsonPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx generator,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null
            ? extendEncode(JsonEx.stream(JsonEx.createParser()), schema)
                .transform(schema.validator(lenient))
                .lenient(lenient)
                .reporting(reporter)
                .into(generator)
            : null;
    }

    // folds every installed json model extension's own decode stage(s) into the stream, in discovery
    // order, ahead of this handler's own validator/extractor stages: the canonical value being decoded
    // into the view delivered to a reader
    private JsonStream extendDecode(
        JsonStream stream,
        JsonSchema schema)
    {
        JsonStream extended = stream;
        for (JsonModelExtContext ext : exts)
        {
            extended = ext.supplyHandler(schema, options).decode(extended);
        }
        return extended;
    }

    // folds every installed json model extension's own encode stage(s) into the stream, in discovery
    // order, ahead of this handler's own validator stage: a caller's value being encoded into its
    // canonical form
    private JsonStream extendEncode(
        JsonStream stream,
        JsonSchema schema)
    {
        JsonStream extended = stream;
        for (JsonModelExtContext ext : exts)
        {
            extended = ext.supplyHandler(schema, options).encode(extended);
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
