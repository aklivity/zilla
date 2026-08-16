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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnvelope;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtContext;

// Per-worker factory for a protobuf model. One handler serves both directions: supplyDecoder vends a
// per-stream ProtobufModelDecoderPipeline (catalog framing and message-index prefix stripped, value validated and
// re-encoded as JSON or canonical wire) and supplyEncoder vends a per-stream ProtobufModelEncoderPipeline
// (catalog framing and message-index prefix emitted, value validated). Configuration-derived state (catalog,
// schema cache, extraction paths) is shared; in-flight state lives on each pipeline.
public final class ProtobufModelHandlerImpl extends ProtobufModelHandler implements ModelHandler
{
    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    private final Map<String, Object> jsonConfig;
    // the encode-side message-index path is fixed per schemaId (catalog.record never changes at runtime),
    // so resolving it is cached exactly like supplySchema/supplyIndexPadding rather than re-derived (and
    // defensively re-cloned by ProtobufSchema.messageIndexes) on every encode call
    private final Int2ObjectCache<int[]> messagePaths;
    private final ProtobufModelConfig options;
    private final List<ProtobufModelExtContext> exts;

    public ProtobufModelHandlerImpl(
        ProtobufModelConfig config,
        EngineContext context,
        List<ProtobufModelExtContext> exts)
    {
        super(config, context);
        this.jsonConfig = new HashMap<>();
        jsonConfig.put(ProtobufJson.FIELD_NAMES, ProtobufJson.FieldNames.PROTO);
        jsonConfig.put(ProtobufJson.INCLUDE_DEFAULTS, Boolean.TRUE);
        this.messagePaths = new Int2ObjectCache<>(1, 1024, i -> {});
        this.options = config;
        this.exts = exts;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return new ProtobufModelDecoderPipeline(this, ProtobufModelEnvelope.of(requireNonNull(envelope)),
            requireNonNull(transform));
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return new ProtobufModelEncoderPipeline(this, ProtobufModelEnvelope.of(requireNonNull(envelope)));
    }

    int decodePadding(
        DirectBufferEx data,
        int index,
        int length)
    {
        int schemaId = resolveSchemaId(data, index, length);
        int padding = handler.decodePadding(data, index, length) + supplyExtPadding(schemaId);
        if (VIEW_JSON.equals(view))
        {
            padding += supplyJsonFormatPadding(schemaId);
        }
        return padding;
    }

    @Override
    protected int extPadding(
        ProtobufSchema schema)
    {
        int padding = 0;
        if (schema != null)
        {
            for (ProtobufModelExtContext ext : exts)
            {
                padding += ext.supplyHandler(schema, options).padding(schema);
            }
        }
        return padding;
    }

    int encodePadding(
        int length)
    {
        int schemaId = resolveSchemaId();
        return handler.encodePadding(length) + supplyIndexPadding(schemaId) + supplyExtPadding(schemaId);
    }

    // the catalog framing the value carries on the wire, stripped once at the start of the first fragment
    int prefix(
        DirectBufferEx data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
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
        return catalog != null && catalog.id > 0
            ? catalog.id
            : handler.resolve(subject, catalog.version);
    }

    // consumes the message-index varints at the value start (after the catalog framing), returning the number
    // of bytes they occupy; the decoded path is then read by message(int)
    int messageProgress(
        DirectBufferEx data,
        int index,
        int length)
    {
        return decodeIndexes(data, index, length);
    }

    ProtobufMessage message(
        int schemaId)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        return schema != null ? schema.messageByIndexes(decodedPath()) : null;
    }

    // avoids computeIfAbsent for the same reason as ProtobufModelHandler.supplySchema: a capturing method
    // reference argument is allocated on every call, not just on a cache miss
    int[] messagePath(
        int schemaId)
    {
        int[] path = messagePaths.get(schemaId);
        if (path == null)
        {
            path = resolveMessagePath(schemaId);
            if (path != null)
            {
                messagePaths.put(schemaId, path);
            }
        }
        return path;
    }

    private int[] resolveMessagePath(
        int schemaId)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        return schema != null && catalog.record != null ? schema.messageIndexes(catalog.record) : null;
    }

    ProtobufMessage message(
        int schemaId,
        int[] path)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        return schema != null && path != null ? schema.messageByIndexes(path) : null;
    }

    // writes the catalog framing prefix for the resolved schema id into next, returning the bytes written
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

    // the message-index framing the wire value carries ahead of its payload, the single zero byte for the
    // first top-level message and the zigzag-varint index path otherwise
    byte[] indexFraming(
        int[] path)
    {
        encodeIndexes(path);
        byte[] framing;
        if (indexes.size() == 2 && indexes.getInt(0) == 1 && indexes.getInt(1) == 0)
        {
            framing = ZERO_INDEX;
        }
        else
        {
            framing = encodeIndexes();
        }
        indexes.clear();
        return framing;
    }

    // read-direction pipeline; when extractor is null no extractor stage is wired so the verbatim/SEGMENTED
    // fast path stays in effect for a decode with no field extraction
    ProtobufPipeline newPipeline(
        int schemaId,
        boolean lenient,
        String messageName,
        ProtobufExtractor extractor,
        ProtobufReporter reporter,
        ProtobufEnvelope envelope)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        ProtobufPipeline pipeline = null;
        if (schema != null && messageName != null)
        {
            // a json view re-encodes the wire message into JSON; any other view re-encodes it into canonical
            // wire, so the bytes the parser validated are reproduced for the caller
            ProtobufGenerator generator = VIEW_JSON.equals(view)
                ? ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName, jsonConfig)
                : Protobuf.generator();
            pipeline = extractor != null
                ? extendDecode(Protobuf.stream(Protobuf.parser(schema, messageName)), schema)
                    .transform(extractor)
                    .lenient(lenient)
                    .reporting(reporter)
                    .envelope(envelope)
                    .into(generator, schema, messageName)
                : extendDecode(Protobuf.stream(Protobuf.parser(schema, messageName)), schema)
                    .lenient(lenient)
                    .reporting(reporter)
                    .envelope(envelope)
                    .into(generator, schema, messageName);
        }
        return pipeline;
    }

    ProtobufPipeline newPipeline(
        int schemaId,
        boolean lenient,
        String messageName,
        ProtobufReporter reporter,
        ProtobufEnvelope envelope)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        ProtobufPipeline pipeline = null;
        if (schema != null && messageName != null)
        {
            // a json view parses JSON into the wire message; any other view re-encodes the incoming wire value,
            // validating it against the schema in both cases
            ProtobufParser parser = VIEW_JSON.equals(view)
                ? ProtobufJson.parser(JsonEx.createParser(), schema, messageName,
                    Map.of(ProtobufJson.REJECT_UNKNOWN_FIELDS, Boolean.TRUE))
                : Protobuf.parser(schema, messageName);
            pipeline = extendEncode(Protobuf.stream(parser), schema)
                .lenient(lenient)
                .reporting(reporter)
                .envelope(envelope)
                .into(Protobuf.generator(), schema, messageName);
        }
        return pipeline;
    }

    // folds every installed protobuf model extension's own decode stage(s) into the stream, in discovery
    // order, ahead of this handler's own extractor stage: the canonical value being decoded into the view
    // delivered to a reader
    private ProtobufStream extendDecode(
        ProtobufStream stream,
        ProtobufSchema schema)
    {
        ProtobufStream extended = stream;
        for (ProtobufModelExtContext ext : exts)
        {
            extended = ext.supplyHandler(schema, options).decode(extended);
        }
        return extended;
    }

    // folds every installed protobuf model extension's own encode stage(s) into the stream, in discovery
    // order, ahead of this handler's own extractor stage: a caller's value being encoded into its
    // canonical form
    private ProtobufStream extendEncode(
        ProtobufStream stream,
        ProtobufSchema schema)
    {
        ProtobufStream extended = stream;
        for (ProtobufModelExtContext ext : exts)
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
