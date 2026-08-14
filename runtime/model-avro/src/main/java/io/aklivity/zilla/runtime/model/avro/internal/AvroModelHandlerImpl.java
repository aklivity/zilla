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
import static java.util.Objects.requireNonNull;

import java.util.List;

import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroReporter;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroStream;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.avro.json.AvroJson;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtContext;

// Per-worker factory for an Avro model. One handler serves both directions: supplyDecoder vends a
// per-stream AvroModelDecoderPipeline (catalog framing stripped, value validated and re-encoded) and supplyEncoder
// vends a per-stream AvroModelEncoderPipeline (catalog framing emitted, value validated). Configuration-derived
// state (catalog, schema cache, extraction paths) is shared; in-flight state lives on each pipeline.
public final class AvroModelHandlerImpl extends AvroModelHandler implements ModelHandler
{
    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    private final AvroModelConfig options;
    private final List<AvroModelExtContext> exts;

    public AvroModelHandlerImpl(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context,
        List<AvroModelExtContext> exts)
    {
        super(config, options, context);
        this.options = options;
        this.exts = exts;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelTransform transform)
    {
        return new AvroModelDecoderPipeline(this, requireNonNull(transform));
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelTransform transform)
    {
        return new AvroModelEncoderPipeline(this, requireNonNull(transform));
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
            padding += supplyPadding(schemaId);
        }
        return padding;
    }

    @Override
    protected int extPadding(
        AvroSchema schema)
    {
        int padding = 0;
        if (schema != null)
        {
            for (AvroModelExtContext ext : exts)
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

    AvroPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx json,
        AvroTransform adapter,
        AvroReporter reporter)
    {
        AvroSchema schema = supplySchema(schemaId);
        AvroPipeline pipeline = null;
        if (schema != null)
        {
            // a json view re-encodes Avro into JSON; any other view re-encodes Avro into canonical Avro,
            // so the bytes the parser validated are reproduced for the caller
            AvroGenerator generator = VIEW_JSON.equals(view)
                ? AvroJson.generator(schema, json, true)
                : Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0);
            pipeline = extend(Avro.stream(Avro.parser(schema)), schema)
                .transform(adapter)
                .lenient(lenient)
                .reporting(reporter)
                .into(generator);
        }
        return pipeline;
    }

    AvroPipeline newPipeline(
        int schemaId,
        boolean lenient,
        AvroTransform adapter,
        AvroReporter reporter)
    {
        AvroSchema schema = supplySchema(schemaId);
        AvroPipeline pipeline = null;
        if (schema != null)
        {
            // a json view parses JSON input and re-encodes it as Avro binary; any other view validates
            // Avro binary input and reproduces it, so a malformed datum yields a binary "truncated datum"
            // diagnostic rather than a JSON parse failure
            //
            // model extensions are decode-only: encode is the write path into the broker, which must
            // keep the source of truth intact, so extensions never fold into the encoder stream here
            AvroStream stream = VIEW_JSON.equals(view)
                ? AvroJson.stream(schema, JsonEx.createParser(), true)
                : Avro.stream(Avro.parser(schema));
            pipeline = stream
                .transform(adapter)
                .lenient(lenient)
                .reporting(reporter)
                .into(Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0));
        }
        return pipeline;
    }

    // folds every installed avro model extension's own stage(s) into the decoder stream, in discovery
    // order, ahead of this handler's own adapter stage; decode is the read path out of the broker, where
    // a read-side concern like disclosure redaction belongs
    private AvroStream extend(
        AvroStream stream,
        AvroSchema schema)
    {
        AvroStream extended = stream;
        for (AvroModelExtContext ext : exts)
        {
            extended = (AvroStream) ext.supplyHandler(schema, options).transform(extended);
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
