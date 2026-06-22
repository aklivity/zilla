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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

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
// per-stream AvroReadModelPipeline (catalog framing stripped, value validated and re-encoded) and supplyEncoder
// vends a per-stream AvroWriteModelPipeline (catalog framing emitted, value validated). Configuration-derived
// state (catalog, schema cache, extraction paths) is shared; in-flight state lives on each pipeline.
public final class AvroModelHandlerImpl extends AvroModelHandler implements ModelHandler
{
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);

    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    private final Matcher matcher;
    private final List<String> paths;
    private final List<String> names;

    public AvroModelHandlerImpl(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
        this.matcher = PATH_PATTERN.matcher("");
        this.paths = new ArrayList<>();
        this.names = new ArrayList<>();
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches() && !paths.contains(path))
        {
            paths.add(path);
            names.add(matcher.group(1));
        }
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelVisitor visitor)
    {
        return new AvroReadModelPipeline(this, paths, names, visitor);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelVisitor visitor)
    {
        return new AvroWriteModelPipeline(this);
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
                : Avro.generator(schema, new UnsafeBuffer(new byte[1]), 0);
            pipeline = Avro.stream(Avro.parser(schema))
                .transform(extractor)
                .reporting(reporter)
                .into(generator);
        }
        return pipeline;
    }

    AvroPipeline newPipeline(
        int schemaId,
        AvroReporter reporter)
    {
        AvroSchema schema = supplySchema(schemaId);
        return schema != null
            ? AvroJson.stream(schema, JsonEx.createParser(), true)
                .reporting(reporter)
                .into(Avro.generator(schema, new UnsafeBuffer(new byte[1]), 0))
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
