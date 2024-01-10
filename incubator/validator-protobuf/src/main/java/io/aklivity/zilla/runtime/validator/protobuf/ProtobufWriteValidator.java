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
package io.aklivity.zilla.runtime.validator.protobuf;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;

public class ProtobufWriteValidator extends ProtobufValidator implements ValueValidator, FragmentValidator
{
    private final DirectBuffer indexesRO;
    private final InputStreamReader input;
    private final DirectBufferInputStream in;
    private final JsonFormat.Parser parser;

    public ProtobufWriteValidator(
        ProtobufValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
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
    public int validate(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validateComplete(data, index, length, next);
    }

    @Override
    public int validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        return (flags & FLAGS_FIN) != 0x00
            ? validateComplete(data, index, length, (b, i, l) -> next.accept(FLAGS_COMPLETE, b, i, l))
            : 0;
    }

    private int validateComplete(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        int schemaId = catalog != null && catalog.id > 0
                ? catalog.id
                : handler.resolve(subject, catalog.version);

        if (FORMAT_JSON.equals(format))
        {
            valLength = handler.encode(schemaId, data, index, length, next, this::serializeJsonRecord);
        }
        else if (validate(schemaId, data, index, length))
        {
            valLength = handler.encode(schemaId, data, index, length, next, this::encode);
        }
        return valLength;
    }

    private boolean validate(
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
                    ex.printStackTrace();
                }
            }
        }
        return status;
    }

    private int encode(
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
                        valLength = encode(schemaId, out.buffer(), 0, out.position(), next);
                    }
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        return valLength;
    }
}
