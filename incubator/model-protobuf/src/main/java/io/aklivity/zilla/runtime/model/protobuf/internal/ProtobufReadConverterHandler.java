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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufReadConverterHandler extends ProtobufModelHandler implements ConverterHandler
{
    private final JsonFormat.Printer printer;
    private final OutputStreamWriter output;

    public ProtobufReadConverterHandler(
        ProtobufModelConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
        this.printer = JsonFormat.printer()
            .omittingInsignificantWhitespace()
            .preservingProtoFieldNames()
            .includingDefaultValueFields();
        this.output = new OutputStreamWriter(out);
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        int padding = 0;
        if (VIEW_JSON.equals(view))
        {
            int schemaId = handler.resolve(data, index, length);

            if (schemaId == NO_SCHEMA_ID)
            {
                schemaId = catalog.id != NO_SCHEMA_ID
                    ? catalog.id
                    : handler.resolve(subject, catalog.version);
            }
            padding = supplyJsonFormatPadding(schemaId);
        }
        return padding;
    }

    @Override
    public int convert(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return handler.decode(data, index, length, next, this::decodePayload);
    }

    private int decodePayload(
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

        int progress = decodeIndexes(data, index, length);

        return validate(schemaId, data, index + progress, length - progress, next);
    }

    private int validate(
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
        DescriptorTree tree = supplyDescriptorTree(schemaId);
        if (tree != null)
        {
            Descriptors.Descriptor descriptor = tree.findByIndexes(indexes);
            if (descriptor != null)
            {
                in.wrap(data, index, length);
                DynamicMessage.Builder builder = supplyDynamicMessageBuilder(descriptor);
                validate:
                try
                {
                    DynamicMessage message = builder.mergeFrom(in).build();
                    builder.clear();
                    if (!message.getUnknownFields().asMap().isEmpty())
                    {
                        break validate;
                    }

                    if (VIEW_JSON.equals(view))
                    {
                        out.wrap(out.buffer());
                        printer.appendTo(message, output);
                        output.flush();
                        valLength = out.position();
                        next.accept(out.buffer(), 0, valLength);
                    }
                    else
                    {
                        next.accept(data, index, length);
                        valLength = length;
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
