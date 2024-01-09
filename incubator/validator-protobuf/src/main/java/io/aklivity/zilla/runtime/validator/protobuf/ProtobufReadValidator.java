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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;

public class ProtobufReadValidator  extends ProtobufValidator implements ValueValidator, FragmentValidator
{
    private final JsonFormat.Printer printer;
    private final OutputStreamWriter output;

    public ProtobufReadValidator(
        ProtobufValidatorConfig config,
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
        if (FORMAT_JSON.equals(format))
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
            padding = supplyJsonFormatPadding(schemaId);
        }
        return padding;
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
            tree = tree.findByIndexes(indexes);
            if (tree != null)
            {
                Descriptors.Descriptor descriptor = tree.descriptor;
                in.wrap(data, index, length);
                DynamicMessage.Builder builder = supplyDynamicMessageBuilder(descriptor);
                validate:
                try
                {
                    builder.mergeFrom(in);
                    DynamicMessage message = builder.build();
                    builder.clear();
                    if (!message.getUnknownFields().asMap().isEmpty())
                    {
                        break validate;
                    }

                    if (FORMAT_JSON.equals(format))
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
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return valLength;
    }
}
