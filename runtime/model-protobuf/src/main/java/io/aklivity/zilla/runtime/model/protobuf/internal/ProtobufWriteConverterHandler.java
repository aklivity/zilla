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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufWriteConverterHandler extends ProtobufModelHandler implements ConverterHandler
{
    private final DirectBuffer indexesRO;
    private final Map<String, JsonState> jsonStates;

    public ProtobufWriteConverterHandler(
        ProtobufModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.indexesRO = new UnsafeBuffer();
        this.jsonStates = new HashMap<>();
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        int schemaId = catalog != null && catalog.id > 0
                ? catalog.id
                : handler.resolve(subject, catalog.version);

        return handler.encodePadding(length) + supplyIndexPadding(schemaId);
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
        else
        {
            valLength = handler.encode(traceId, bindingId, schemaId, data, index, length, next, this::serializeRecord);
        }
        return valLength;
    }

    private int serializeRecord(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
        ProtobufSchema schema = supplySchema(schemaId);
        if (schema != null && catalog.record != null)
        {
            int[] path = schema.messageIndexes(catalog.record);
            ProtobufMessage message = schema.messageByIndexes(path);
            if (message != null)
            {
                if (schema.validate(message.name(), buffer, index, length))
                {
                    encodeIndexes(path);
                    valLength = encode(buffer, index, length, next);
                }
                else
                {
                    event.validationFailure(traceId, bindingId, "Invalid Protobuf event");
                }
            }
        }
        return valLength;
    }

    private int serializeJsonRecord(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
        ProtobufSchema schema = supplySchema(schemaId);
        if (schema != null && catalog.record != null)
        {
            int[] path = schema.messageIndexes(catalog.record);
            ProtobufMessage message = schema.messageByIndexes(path);
            if (message != null)
            {
                String messageName = message.name();
                JsonState state = jsonStates.computeIfAbsent(messageName, name -> new JsonState(schema, name));
                out.wrap(out.buffer());
                state.generator.wrap(out.buffer(), 0, out.buffer().capacity());
                state.pipeline.reset();
                ProtobufPipeline.Status status;
                try
                {
                    status = state.pipeline.feed(buffer, index, length);
                }
                catch (Exception ex)
                {
                    status = ProtobufPipeline.Status.REJECTED;
                }
                if (status == ProtobufPipeline.Status.COMPLETED)
                {
                    encodeIndexes(path);
                    valLength = encode(out.buffer(), 0, state.generator.length(), next);
                }
                else
                {
                    event.validationFailure(traceId, bindingId, "Invalid Protobuf event");
                }
            }
        }
        return valLength;
    }

    private int encode(
        DirectBuffer buffer,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength;
        if (indexes.size() == 2 && indexes.get(0) == 1 && indexes.get(1) == 0)
        {
            indexesRO.wrap(ZERO_INDEX);
            valLength = 1;
        }
        else
        {
            indexesRO.wrap(encodeIndexes());
            valLength = indexes.size();
        }
        indexes.clear();
        next.accept(indexesRO, 0, valLength);
        next.accept(buffer, index, length);
        return valLength + length;
    }

    private final class JsonState
    {
        private final ProtobufGenerator generator;
        private final ProtobufPipeline pipeline;

        private JsonState(
            ProtobufSchema schema,
            String messageName)
        {
            this.generator = Protobuf.generator();
            JsonParserEx jsonParser = JsonEx.createParser();
            this.pipeline = Protobuf.stream(ProtobufJson.parser(jsonParser, schema, messageName))
                .into(ProtobufSink.of(generator, schema, messageName));
        }
    }
}
