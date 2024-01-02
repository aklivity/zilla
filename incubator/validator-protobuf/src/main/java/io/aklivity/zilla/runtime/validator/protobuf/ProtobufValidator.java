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
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.io.DirectBufferInputStream;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;

public class ProtobufValidator
{
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("message\\s+(\\w+)\\s*\\{([^}]+)\\}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(\\w+)\\s+(\\w+)\\s*=\\s*(\\d+);");
    private static final Map<String, DescriptorProtos.FieldDescriptorProto.Type> TYPES;

    static
    {
        TYPES = new HashMap<String, DescriptorProtos.FieldDescriptorProto.Type>();
        TYPES.put("double", DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE);
        TYPES.put("float", DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT);
        TYPES.put("int32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32);
        TYPES.put("int64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64);
        TYPES.put("uint32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32);
        TYPES.put("uint64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64);
        TYPES.put("sint32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32);
        TYPES.put("sint64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64);
        TYPES.put("fixed32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32);
        TYPES.put("fixed64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64);
        TYPES.put("sfixed32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32);
        TYPES.put("sfixed64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64);
        TYPES.put("bool", DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL);
        TYPES.put("string", DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING);
        TYPES.put("bytes", DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES);
    }

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;

    private final Int2ObjectCache<Descriptor> descriptors;
    private final DirectBufferInputStream in;
    private final FileDescriptorSet.Builder setBuilder;
    private final FileDescriptorProto.Builder builder;
    private final DescriptorProto.Builder messageBuilder;
    private final FieldDescriptorProto.Builder fieldBuilder;
    private final FileDescriptor[] dependencies;

    protected ProtobufValidator(
        ProtobufValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = supplyCatalog.apply(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.descriptors = new Int2ObjectCache<>(1, 1024, i -> {});
        this.in = new DirectBufferInputStream();
        this.setBuilder = FileDescriptorSet.newBuilder();
        this.builder = FileDescriptorProto.newBuilder();
        this.messageBuilder = DescriptorProto.newBuilder();
        this.fieldBuilder = FieldDescriptorProto.newBuilder();
        this.dependencies = new FileDescriptor[0];
    }

    protected boolean validate(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        Descriptor descriptor = supplyDescriptor(schemaId);
        try
        {
            in.wrap(buffer, index, length);
            DynamicMessage.parseFrom(descriptor, in);
            status = true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return status;
    }

    private Descriptor supplyDescriptor(
        int schemaId)
    {
        return descriptors.computeIfAbsent(schemaId, this::createDescriptor);
    }

    private Descriptor createDescriptor(
        int schemaId)
    {
        Descriptor schema = null;

        String schemaStr = handler.resolve(schemaId);
        if (schemaStr != null)
        {
            Matcher messageMatcher = MESSAGE_PATTERN.matcher(schemaStr);
            while (messageMatcher.find())
            {
                String name = messageMatcher.group(1);
                String field = messageMatcher.group(2);

                messageBuilder.setName(name);
                Matcher fieldMatcher = FIELD_PATTERN.matcher(field);
                while (fieldMatcher.find())
                {
                    String fieldType = fieldMatcher.group(1);
                    String fieldName = fieldMatcher.group(2);
                    int fieldNumber = Integer.parseInt(fieldMatcher.group(3));
                    fieldBuilder.setName(fieldName);
                    fieldBuilder.setNumber(fieldNumber);
                    if (TYPES.containsKey(fieldType))
                    {
                        fieldBuilder.setType(TYPES.get(fieldType));
                    }
                    messageBuilder.addField(fieldBuilder.build());
                }
                builder.addMessageType(messageBuilder);
            }
            setBuilder.addFile(builder);

            FileDescriptorSet file = setBuilder.build();
            FileDescriptor[] descriptors;
            try
            {
                descriptors = new FileDescriptor[]
                    {
                        FileDescriptor.buildFrom(file.getFile(0), dependencies)
                    };
                schema = descriptors[0].getMessageTypes().get(0);
            }
            catch (Descriptors.DescriptorValidationException e)
            {
                e.printStackTrace();
            }
        }
        return schema;
    }
}
