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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.json.AvroJson;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.avro.internal.types.OctetsFW;

public class AvroReadConverterHandler extends AvroModelHandler implements ConverterHandler
{
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();

    private final Matcher matcher;
    private final Int2ObjectCache<AvroToJson> pipelines;

    public AvroReadConverterHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
        this.matcher = PATH_PATTERN.matcher("");
        this.pipelines = new Int2ObjectCache<>(1, 1024, i -> {});
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        int padding = handler.decodePadding(data, index, length);
        if (VIEW_JSON.equals(view))
        {
            int schemaId = handler.resolve(data, index, length);

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
            padding += supplyPadding(schemaId);
        }
        return padding;
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches())
        {
            extracted.put(matcher.group(1), new AvroField());
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
        for (AvroField field : extracted.values())
        {
            field.value.wrap(EMPTY_BUFFER, 0, 0);
        }
        return handler.decode(traceId, bindingId, data, index, length, next, this::decodePayload);
    }

    @Override
    public int extractedLength(
        String path)
    {
        OctetsFW value = null;
        if (matcher.reset(path).matches())
        {
            value = extracted.get(matcher.group(1)).value;
        }
        return value != null ? value.sizeof() : 0;
    }

    @Override
    public void extracted(
        String path,
        FieldVisitor visitor)
    {
        if (matcher.reset(path).matches())
        {
            OctetsFW value = extracted.get(matcher.group(1)).value;
            if (value != null && value.sizeof() != 0)
            {
                visitor.visit(value.buffer(), value.offset(), value.sizeof());
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
        int valLength = -1;

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

        if (VIEW_JSON.equals(view))
        {
            valLength = deserializeRecord(traceId, bindingId, schemaId, data, index, length, next);
            if (valLength != -1 && !extracted.isEmpty())
            {
                validate(traceId, bindingId, schemaId, data, index, length);
            }
        }
        else if (validate(traceId, bindingId, schemaId, data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }
        return valLength;
    }

    private int deserializeRecord(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        AvroToJson pipeline = supplyPipeline(schemaId);
        if (pipeline != null)
        {
            int limit = outLimit(length);
            int accumulated = 0;
            pipeline.generator.wrap(out, 0, limit);
            pipeline.pipeline.reset();
            Status status = pipeline.pipeline.feed(data, index, index + length, true);
            while (status == Status.SUSPENDED)
            {
                pipeline.json.flush();
                int chunk = pipeline.json.length();
                accumulator.putBytes(accumulated, out, 0, chunk);
                accumulated += chunk;
                pipeline.generator.wrap(out, 0, limit);
                status = pipeline.pipeline.feed(data, index, index + length, true);
            }

            if (status == Status.COMPLETED)
            {
                pipeline.json.flush();
                int chunk = pipeline.json.length();
                if (accumulated == 0)
                {
                    next.accept(out, 0, chunk);
                    valLength = chunk;
                }
                else
                {
                    accumulator.putBytes(accumulated, out, 0, chunk);
                    accumulated += chunk;
                    next.accept(accumulator, 0, accumulated);
                    valLength = accumulated;
                }
            }
            else
            {
                event.validationFailure(traceId, bindingId, "Invalid Avro encoding");
            }
        }
        return valLength;
    }

    private AvroToJson supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId, this::newPipeline);
    }

    private AvroToJson newPipeline(
        int schemaId)
    {
        AvroToJson pipeline = null;
        AvroSchema schema = supplySchema(schemaId);
        if (schema != null)
        {
            JsonGeneratorEx json = JsonEx.createGenerator();
            AvroGenerator generator = AvroJson.generator(schema, json, true);
            AvroPipeline avro = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
            pipeline = new AvroToJson(avro, json, generator);
        }
        return pipeline;
    }

    private static final class AvroToJson
    {
        private final AvroPipeline pipeline;
        private final JsonGeneratorEx json;
        private final AvroGenerator generator;

        private AvroToJson(
            AvroPipeline pipeline,
            JsonGeneratorEx json,
            AvroGenerator generator)
        {
            this.pipeline = pipeline;
            this.json = json;
            this.generator = generator;
        }
    }
}
