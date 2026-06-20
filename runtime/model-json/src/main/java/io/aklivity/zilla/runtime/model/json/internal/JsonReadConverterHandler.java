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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonReadConverterHandler extends JsonModelHandler implements ConverterHandler
{
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);
    private static final int OUTPUT_CAPACITY = 8192;

    private final Matcher matcher;
    private final JsonExtractor extractor;
    private final JsonGeneratorEx generator;
    private final MutableDirectBuffer output;
    private final Int2ObjectCache<JsonPipeline> pipelines;

    private String diagnostic;

    public JsonReadConverterHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.matcher = PATH_PATTERN.matcher("");
        this.extractor = new JsonExtractor();
        this.output = new UnsafeBuffer(new byte[OUTPUT_CAPACITY]);
        this.generator = JsonEx.createGenerator();
        this.pipelines = new Int2ObjectCache<>(1, 16, p -> {});
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches())
        {
            extractor.register(matcher.group(1));
        }
    }

    @Override
    public int convert(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return handler.decode(traceId, bindingId, data, index, length, next, this::decodePayload);
    }

    @Override
    public int extractedLength(
        String path)
    {
        return matcher.reset(path).matches() ? extractor.length(matcher.group(1)) : 0;
    }

    @Override
    public void extracted(
        String path,
        FieldVisitor visitor)
    {
        if (matcher.reset(path).matches())
        {
            String name = matcher.group(1);
            int length = extractor.length(name);
            if (length != 0)
            {
                visitor.visit(extractor.value(name), 0, length);
            }
        }
    }

    private int decodePayload(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        if (schemaId == NO_SCHEMA_ID)
        {
            if (catalog.id != NO_SCHEMA_ID)
            {
                schemaId = catalog.id;
            }
            else
            {
                schemaId = handler.resolve(subject, catalog.version);
            }
        }

        JsonPipeline pipeline = schemaId != NO_SCHEMA_ID ? supplyPipeline(schemaId) : null;

        return pipeline != null
            ? serialize(traceId, bindingId, pipeline, data, index, length, next)
            : -1;
    }

    private int serialize(
        long traceId,
        long bindingId,
        JsonPipeline pipeline,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        pipeline.reset();
        diagnostic = null;

        int produced = 0;
        Status status;
        do
        {
            generator.wrap(output, 0, OUTPUT_CAPACITY);
            status = pipeline.feed(data, index, index + length, true);
            int chunk = generator.length();
            if (chunk > 0 && status != Status.REJECTED)
            {
                next.accept(output, 0, chunk);
                produced += chunk;
            }
        }
        while (status == Status.SUSPENDED);

        int valLength = -1;
        if (status == Status.COMPLETED)
        {
            valLength = produced;
        }
        else
        {
            event.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : JsonModel.NAME);
        }
        return valLength;
    }

    private void onRejected(
        JsonDiagnostic diagnostic)
    {
        this.diagnostic = diagnostic.message();
    }

    private JsonPipeline supplyPipeline(
        int schemaId)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null ? pipelines.computeIfAbsent(schemaId, id -> newPipeline(schema)) : null;
    }

    private JsonPipeline newPipeline(
        JsonSchema schema)
    {
        return JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator())
            .transform(extractor)
            .reporting(this::onRejected)
            .into(JsonEx.createSink(generator));
    }
}
