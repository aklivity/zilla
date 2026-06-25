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
package io.aklivity.zilla.runtime.model.avro.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import org.agrona.DirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroReporter;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.json.AvroJson;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

// Per-worker factory for an Avro model. One handler serves both directions: supplyDecoder vends a
// per-stream AvroModelDecoderPipeline (catalog framing stripped, value validated and re-encoded) and supplyEncoder
// vends a per-stream AvroModelEncoderPipeline (catalog framing emitted, value validated). Configuration-derived
// state (catalog, schema cache, extraction paths) is shared; in-flight state lives on each pipeline.
public final class AvroModelHandlerImpl extends AvroModelHandler implements ModelHandler
{
    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    public AvroModelHandlerImpl(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelVisitor visitor)
    {
        return new AvroModelDecoderPipeline(this, visitor);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelVisitor visitor)
    {
        return new AvroModelEncoderPipeline(this);
    }

    int decodePadding(
        DirectBuffer data,
        int index,
        int length)
    {
        int padding = handler.decodePadding(data, index, length);
        if (VIEW_JSON.equals(view))
        {
            padding += supplyPadding(resolveSchemaId(data, index, length));
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
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
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

    AvroPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx json,
        AvroExtractor extractor,
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
            pipeline = Avro.stream(Avro.parser(schema))
                .transform(extractor)
                .lenient(lenient)
                .reporting(reporter)
                .into(generator);
        }
        return pipeline;
    }

    // read-direction pipeline without the extractor stage, used when no field extraction is requested so the
    // verbatim/SEGMENTED fast path stays in effect
    AvroPipeline newPipeline(
        int schemaId,
        boolean lenient,
        JsonGeneratorEx json,
        AvroReporter reporter)
    {
        AvroSchema schema = supplySchema(schemaId);
        AvroPipeline pipeline = null;
        if (schema != null)
        {
            AvroGenerator generator = VIEW_JSON.equals(view)
                ? AvroJson.generator(schema, json, true)
                : Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0);
            pipeline = Avro.stream(Avro.parser(schema))
                .lenient(lenient)
                .reporting(reporter)
                .into(generator);
        }
        return pipeline;
    }

    AvroPipeline newPipeline(
        int schemaId,
        boolean lenient,
        AvroReporter reporter)
    {
        AvroSchema schema = supplySchema(schemaId);
        AvroPipeline pipeline = null;
        if (schema != null)
        {
            // a json view parses JSON input and re-encodes it as Avro binary; any other view validates
            // Avro binary input and reproduces it, so a malformed datum yields a binary "truncated datum"
            // diagnostic rather than a JSON parse failure
            pipeline = VIEW_JSON.equals(view)
                ? AvroJson.stream(schema, JsonEx.createParser(), true)
                    .lenient(lenient)
                    .reporting(reporter)
                    .into(Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0))
                : Avro.stream(Avro.parser(schema))
                    .lenient(lenient)
                    .reporting(reporter)
                    .into(Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0));
        }
        return pipeline;
    }

    void validationFailure(
        long traceId,
        long bindingId,
        String diagnostic)
    {
        event.validationFailure(traceId, bindingId, diagnostic);
    }
}
