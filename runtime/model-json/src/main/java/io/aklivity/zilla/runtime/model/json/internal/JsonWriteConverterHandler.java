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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonWriteConverterHandler extends JsonModelHandler implements ConverterHandler
{
    private static final int OUTPUT_CAPACITY = 8192;

    private final JsonGeneratorEx generator;
    private final MutableDirectBuffer output;
    private final JsonPipeline serializer;

    public JsonWriteConverterHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.output = new UnsafeBuffer(new byte[OUTPUT_CAPACITY]);
        this.generator = JsonEx.createGenerator();
        this.serializer = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));
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

        if (validate(traceId, bindingId, schemaId, data, index, length))
        {
            valLength = handler.encode(traceId, bindingId, schemaId, data, index, length, next, this::encode);
        }
        return valLength;
    }

    private int encode(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        serializer.reset();

        int produced = 0;
        Status status;
        do
        {
            generator.wrap(output, 0, OUTPUT_CAPACITY);
            status = serializer.feed(data, index, index + length, true);
            int chunk = generator.length();
            if (chunk > 0 && status != Status.REJECTED)
            {
                next.accept(output, 0, chunk);
                produced += chunk;
            }
        }
        while (status == Status.SUSPENDED);

        return status == Status.COMPLETED ? produced : -1;
    }
}
