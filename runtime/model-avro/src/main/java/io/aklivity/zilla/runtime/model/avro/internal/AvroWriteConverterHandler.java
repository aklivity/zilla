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

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.json.AvroJson;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroWriteConverterHandler extends AvroModelHandler implements ConverterHandler
{
    private final Int2ObjectCache<JsonToAvro> pipelines;

    public AvroWriteConverterHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
        this.pipelines = new Int2ObjectCache<>(1, 1024, i -> {});
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.encodePadding(length);
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
        int valLength = -1;

        int schemaId = catalog != null && catalog.id > 0
                ? catalog.id
                : handler.resolve(subject, catalog.version);

        if (VIEW_JSON.equals(view))
        {
            valLength = handler.encode(traceId, bindingId, schemaId, data, index, length, next, this::serializeJsonRecord);
        }
        else if (validate(traceId, bindingId, schemaId, data, index, length))
        {
            valLength = handler.encode(traceId, bindingId, schemaId, data, index, length, next, CatalogHandler.Encoder.IDENTITY);
        }
        return valLength;
    }

    private int serializeJsonRecord(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        JsonToAvro pipeline = supplyPipeline(schemaId);
        if (pipeline != null)
        {
            // Avro binary is never more than OUT_SCALE times the JSON it encodes (a double is 8 bytes from a
            // one-character number), so the bounded window admits the whole datum and the feed completes once
            pipeline.generator.wrap(out, 0, outLimit(length));
            pipeline.pipeline.reset();
            Status status = pipeline.pipeline.feed(data, index, length, true);
            if (status == Status.COMPLETED)
            {
                int chunk = pipeline.generator.length();
                next.accept(out, 0, chunk);
                valLength = chunk;
            }
            else
            {
                event.validationFailure(traceId, bindingId, "Invalid JSON encoding");
            }
        }
        return valLength;
    }

    private JsonToAvro supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId, this::newPipeline);
    }

    private JsonToAvro newPipeline(
        int schemaId)
    {
        JsonToAvro pipeline = null;
        AvroSchema schema = supplySchema(schemaId);
        if (schema != null)
        {
            JsonParserEx parser = JsonEx.createParser();
            AvroGenerator generator = Avro.generator(schema, out, 0);
            AvroPipeline avro = AvroJson.stream(schema, parser, true).into(AvroSink.of(generator));
            pipeline = new JsonToAvro(avro, generator);
        }
        return pipeline;
    }

    private static final class JsonToAvro
    {
        private final AvroPipeline pipeline;
        private final AvroGenerator generator;

        private JsonToAvro(
            AvroPipeline pipeline,
            AvroGenerator generator)
        {
            this.pipeline = pipeline;
            this.generator = generator;
        }
    }
}
