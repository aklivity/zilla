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
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;

public class ProtobufReadValidator  extends ProtobufValidator implements ValueValidator, FragmentValidator
{
    public ProtobufReadValidator(
        ProtobufValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        super(config, supplyCatalog);
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return FragmentValidator.super.padding(data, index, length);
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

        int progress = decodeIndexes(data, index, length);
        int currentIndex = index + progress;
        int remainingLength = length - progress;

        if (validate(schemaId, data, currentIndex, remainingLength))
        {
            next.accept(data, currentIndex, remainingLength);
            valLength = remainingLength;
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
        Descriptors.FileDescriptor fileDescriptor = supplyDescriptor(schemaId);
        if (fileDescriptor != null)
        {
            DescriptorTree tree = new DescriptorTree(fileDescriptor).findByIndexes(indexes);
            if (tree != null)
            {
                Descriptors.Descriptor descriptor = tree.descriptor;
                try
                {
                    in.wrap(buffer, index, length);
                    DynamicMessage message = DynamicMessage.parseFrom(descriptor, in);
                    status = message.getUnknownFields().asMap().isEmpty();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return status;
    }
}
