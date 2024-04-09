/*
 * Copyright 2021-2023 Aklivity Inc
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

import java.io.IOException;
import java.io.InputStreamReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufWriteConverterHandler extends ProtobufModelHandler implements ConverterHandler
{
    private final DirectBuffer indexesRO;
    private final InputStreamReader input;
    private final DirectBufferInputStream in;
    private final JsonFormat.Parser parser;

    public ProtobufWriteConverterHandler(
        ProtobufModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.indexesRO = new UnsafeBuffer();
        this.in =  new DirectBufferInputStream();
        this.input = new InputStreamReader(in);
        this.parser = JsonFormat.parser();
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

        return handler.encodePadding() + supplyIndexPadding(schemaId);
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
            valLength = handler.encode(traceId, bindingId, schemaId, data, index, length, next, this::encode);
        }
        return valLength;
    }

    private boolean validate(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        DescriptorTree trees = supplyDescriptorTree(schemaId);
        if (trees != null && catalog.record != null)
        {
            DescriptorTree tree = trees.findByName(catalog.record);
            if (tree != null)
            {
                Descriptors.Descriptor descriptor = tree.descriptor;
                indexes.clear();
                indexes.add(tree.indexes.size());
                indexes.addAll(tree.indexes);
                in.wrap(buffer, index, length);
                DynamicMessage.Builder builder = supplyDynamicMessageBuilder(descriptor);
                try
                {
                    DynamicMessage message = builder.mergeFrom(in).build();
                    builder.clear();
                    status = message.getUnknownFields().asMap().isEmpty();
                }
                catch (IOException ex)
                {
                    event.validationFailure(traceId, bindingId, ex.getMessage());
                }
            }
        }
        return status;
    }

    private int encode(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
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
        DescriptorTree tree = supplyDescriptorTree(schemaId);
        if (tree != null && catalog.record != null)
        {
            tree = tree.findByName(catalog.record);
            if (tree != null)
            {
                Descriptors.Descriptor descriptor = tree.descriptor;
                indexes.clear();
                indexes.add(tree.indexes.size());
                indexes.addAll(tree.indexes);
                DynamicMessage.Builder builder = supplyDynamicMessageBuilder(descriptor);
                in.wrap(buffer, index, length);
                try
                {
                    parser.merge(input, builder);
                    DynamicMessage message = builder.build();
                    builder.clear();
                    if (message.isInitialized() && message.getUnknownFields().asMap().isEmpty())
                    {
                        out.wrap(out.buffer());
                        message.writeTo(out);
                        valLength = encode(traceId, bindingId, schemaId, out.buffer(), 0, out.position(), next);
                    }
                }
                catch (IOException ex)
                {
                    event.validationFailure(traceId, bindingId, ex.getMessage());
                }
            }
        }
        return valLength;
    }
}
