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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Compiles a serialized {@code google.protobuf.FileDescriptorSet} into a {@link ProtobufSchema}.
 * <p>
 * {@code descriptor.proto} is itself Protobuf, so this decodes the descriptor set with the same
 * {@link ProtobufReader} the rest of the library uses — no {@code protobuf-java} dependency. Full
 * names are assembled from the file package and nested type path; composite field {@code type_name}
 * references (leading-dot fully-qualified in the descriptor) are normalized to the dotless full
 * names this model keys on.
 */
public final class DescriptorSetCompiler
{
    private static final int LABEL_REPEATED = 3;

    public ProtobufSchema compile(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        ProtobufSchema.Builder schema = ProtobufSchema.builder();
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1 && wireType == ProtobufWireType.LEN)
            {
                int len = reader.readLength();
                int at = reader.offset();
                reader.skip(len);
                compileFile(buffer, at, len, schema);
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        return schema.build();
    }

    private void compileFile(
        DirectBuffer buffer,
        int offset,
        int length,
        ProtobufSchema.Builder schema)
    {
        String packageName = "";
        List<int[]> messages = new ArrayList<>();
        List<int[]> enums = new ArrayList<>();

        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            switch (number)
            {
            case 2:
                packageName = readString(reader);
                break;
            case 4:
                messages.add(region(reader));
                break;
            case 5:
                enums.add(region(reader));
                break;
            default:
                reader.skipField(wireType);
                break;
            }
        }

        for (int[] message : messages)
        {
            compileMessage(buffer, message[0], message[1], packageName, schema);
        }
        for (int[] enumeration : enums)
        {
            compileEnum(buffer, enumeration[0], enumeration[1], packageName, schema);
        }
    }

    private void compileMessage(
        DirectBuffer buffer,
        int offset,
        int length,
        String prefix,
        ProtobufSchema.Builder schema)
    {
        String name = "";
        boolean mapEntry = false;
        List<String> oneofs = new ArrayList<>();
        List<int[]> fields = new ArrayList<>();
        List<int[]> nested = new ArrayList<>();
        List<int[]> enums = new ArrayList<>();
        int[] options = null;

        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            switch (number)
            {
            case 1:
                name = readString(reader);
                break;
            case 2:
                fields.add(region(reader));
                break;
            case 3:
                nested.add(region(reader));
                break;
            case 4:
                enums.add(region(reader));
                break;
            case 7:
                options = region(reader);
                break;
            case 8:
                oneofs.add(readOneofName(buffer, region(reader)));
                break;
            default:
                reader.skipField(wireType);
                break;
            }
        }

        if (options != null)
        {
            mapEntry = readMapEntry(buffer, options[0], options[1]);
        }

        String fullName = prefix.isEmpty() ? name : prefix + "." + name;
        ProtobufMessage.Builder message = ProtobufMessage.builder(fullName).mapEntry(mapEntry);
        for (int[] field : fields)
        {
            message.field(compileField(buffer, field[0], field[1], oneofs));
        }
        schema.message(message.build());

        for (int[] type : nested)
        {
            compileMessage(buffer, type[0], type[1], fullName, schema);
        }
        for (int[] enumeration : enums)
        {
            compileEnum(buffer, enumeration[0], enumeration[1], fullName, schema);
        }
    }

    private ProtobufField compileField(
        DirectBuffer buffer,
        int offset,
        int length,
        List<String> oneofs)
    {
        String name = "";
        String jsonName = null;
        String typeName = null;
        int fieldNumber = 0;
        int label = 0;
        int typeNumber = 0;
        int oneofIndex = -1;
        boolean proto3Optional = false;
        Boolean packed = null;

        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            switch (number)
            {
            case 1:
                name = readString(reader);
                break;
            case 3:
                fieldNumber = reader.readVarint32();
                break;
            case 4:
                label = reader.readVarint32();
                break;
            case 5:
                typeNumber = reader.readVarint32();
                break;
            case 6:
                typeName = stripLeadingDot(readString(reader));
                break;
            case 8:
                int[] fieldOptions = region(reader);
                packed = readPacked(buffer, fieldOptions[0], fieldOptions[1]);
                break;
            case 9:
                oneofIndex = reader.readVarint32();
                break;
            case 10:
                jsonName = readString(reader);
                break;
            case 17:
                proto3Optional = reader.readVarint64() != 0L;
                break;
            default:
                reader.skipField(wireType);
                break;
            }
        }

        ProtobufField.Builder field = ProtobufField.builder()
            .number(fieldNumber)
            .name(name)
            .type(ProtobufType.ofNumber(typeNumber))
            .repeated(label == LABEL_REPEATED)
            .proto3Optional(proto3Optional);
        if (jsonName != null)
        {
            field.jsonName(jsonName);
        }
        if (typeName != null)
        {
            field.typeName(typeName);
        }
        if (packed != null)
        {
            field.packed(packed);
        }
        if (oneofIndex >= 0 && !proto3Optional && oneofIndex < oneofs.size())
        {
            field.oneof(oneofs.get(oneofIndex));
        }
        return field.build();
    }

    private void compileEnum(
        DirectBuffer buffer,
        int offset,
        int length,
        String prefix,
        ProtobufSchema.Builder schema)
    {
        String name = "";
        List<int[]> values = new ArrayList<>();

        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            switch (number)
            {
            case 1:
                name = readString(reader);
                break;
            case 2:
                values.add(region(reader));
                break;
            default:
                reader.skipField(wireType);
                break;
            }
        }

        String fullName = prefix.isEmpty() ? name : prefix + "." + name;
        ProtobufEnum.Builder enumeration = ProtobufEnum.builder(fullName);
        for (int[] value : values)
        {
            compileEnumValue(buffer, value[0], value[1], enumeration);
        }
        schema.enumeration(enumeration.build());
    }

    private void compileEnumValue(
        DirectBuffer buffer,
        int offset,
        int length,
        ProtobufEnum.Builder enumeration)
    {
        String name = "";
        int number = 0;
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int field = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (field == 1)
            {
                name = readString(reader);
            }
            else if (field == 2)
            {
                number = reader.readVarint32();
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        enumeration.value(name, number);
    }

    private String readOneofName(
        DirectBuffer buffer,
        int[] region)
    {
        String name = "";
        ProtobufReader reader = new ProtobufReader().wrap(buffer, region[0], region[1]);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int field = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (field == 1)
            {
                name = readString(reader);
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        return name;
    }

    private boolean readMapEntry(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        boolean mapEntry = false;
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int field = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (field == 7)
            {
                mapEntry = reader.readVarint64() != 0L;
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        return mapEntry;
    }

    private Boolean readPacked(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        Boolean packed = null;
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int field = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (field == 2)
            {
                packed = reader.readVarint64() != 0L;
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        return packed;
    }

    private int[] region(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int offset = reader.offset();
        reader.skip(length);
        return new int[]{offset, length};
    }

    private String readString(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int at = reader.offset();
        String value = reader.buffer().getStringWithoutLengthUtf8(at, length);
        reader.skip(length);
        return value;
    }

    private static String stripLeadingDot(
        String typeName)
    {
        return typeName.startsWith(".") ? typeName.substring(1) : typeName;
    }
}
