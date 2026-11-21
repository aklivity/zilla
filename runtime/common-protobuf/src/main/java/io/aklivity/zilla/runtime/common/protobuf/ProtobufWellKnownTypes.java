/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.protobuf;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code google.protobuf} well-known types — the nine scalar wrappers ({@code StringValue}, {@code BytesValue},
 * {@code BoolValue}, {@code Int32Value}, {@code UInt32Value}, {@code Int64Value}, {@code UInt64Value},
 * {@code FloatValue}, {@code DoubleValue}) plus {@code Timestamp}, {@code Duration}, {@code Empty},
 * {@code FieldMask}, {@code Any}, {@code Struct}, {@code Value}, {@code ListValue} and the {@code NullValue}
 * enum. Each has a wire shape fixed by the protobuf spec itself, so a schema referencing one by name need not
 * declare it, and importing its {@code google/protobuf/*.proto} file needs no file resolution.
 */
public final class ProtobufWellKnownTypes
{
    private static final String NULL_VALUE = "google.protobuf.NullValue";

    private static final Map<String, ProtobufType> VALUE_TYPES = Map.of(
        "google.protobuf.StringValue", ProtobufType.STRING,
        "google.protobuf.BytesValue", ProtobufType.BYTES,
        "google.protobuf.BoolValue", ProtobufType.BOOL,
        "google.protobuf.Int32Value", ProtobufType.INT32,
        "google.protobuf.UInt32Value", ProtobufType.UINT32,
        "google.protobuf.Int64Value", ProtobufType.INT64,
        "google.protobuf.UInt64Value", ProtobufType.UINT64,
        "google.protobuf.FloatValue", ProtobufType.FLOAT,
        "google.protobuf.DoubleValue", ProtobufType.DOUBLE);

    private static final Set<String> MESSAGE_NAMES = Set.of(
        "google.protobuf.Timestamp",
        "google.protobuf.Duration",
        "google.protobuf.Empty",
        "google.protobuf.FieldMask",
        "google.protobuf.Any",
        "google.protobuf.Struct",
        "google.protobuf.Struct.FieldsEntry",
        "google.protobuf.Value",
        "google.protobuf.ListValue");

    /**
     * The fully-qualified names of all nine well-known wrapper types.
     */
    public static Set<String> names()
    {
        return VALUE_TYPES.keySet();
    }

    /**
     * Whether {@code typeName} names any well-known message or enum type.
     */
    public static boolean defined(
        String typeName)
    {
        return VALUE_TYPES.containsKey(typeName) || MESSAGE_NAMES.contains(typeName) || NULL_VALUE.equals(typeName);
    }

    /**
     * Whether {@code typeName} names the well-known {@code NullValue} enum.
     */
    public static boolean enumeration(
        String typeName)
    {
        return NULL_VALUE.equals(typeName);
    }

    /**
     * The well-known {@code NullValue} enum.
     */
    public static ProtobufEnum nullValue()
    {
        return ProtobufEnum.builder(NULL_VALUE).value("NULL_VALUE", 0).build();
    }

    /**
     * Freshly built descriptors for the well-known messages other than the wrappers; their message and enum
     * fields are unresolved, so a schema links them like its own declared messages.
     */
    public static List<ProtobufMessage> messages()
    {
        return List.of(
            ProtobufMessage.builder("google.protobuf.Timestamp")
                .field(scalar(1, "seconds", ProtobufType.INT64))
                .field(scalar(2, "nanos", ProtobufType.INT32))
                .build(),
            ProtobufMessage.builder("google.protobuf.Duration")
                .field(scalar(1, "seconds", ProtobufType.INT64))
                .field(scalar(2, "nanos", ProtobufType.INT32))
                .build(),
            ProtobufMessage.builder("google.protobuf.Empty")
                .build(),
            ProtobufMessage.builder("google.protobuf.FieldMask")
                .field(ProtobufField.builder().number(1).name("paths").type(ProtobufType.STRING).repeated(true).build())
                .build(),
            ProtobufMessage.builder("google.protobuf.Any")
                .field(scalar(1, "type_url", ProtobufType.STRING))
                .field(scalar(2, "value", ProtobufType.BYTES))
                .build(),
            ProtobufMessage.builder("google.protobuf.Struct")
                .field(ProtobufField.builder().number(1).name("fields").type(ProtobufType.MESSAGE).repeated(true)
                    .typeName("google.protobuf.Struct.FieldsEntry").build())
                .build(),
            ProtobufMessage.builder("google.protobuf.Struct.FieldsEntry")
                .mapEntry(true)
                .field(scalar(1, "key", ProtobufType.STRING))
                .field(ProtobufField.builder().number(2).name("value").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Value").build())
                .build(),
            ProtobufMessage.builder("google.protobuf.Value")
                .field(ProtobufField.builder().number(1).name("null_value").type(ProtobufType.ENUM)
                    .typeName(NULL_VALUE).oneof("kind").build())
                .field(ProtobufField.builder().number(2).name("number_value").type(ProtobufType.DOUBLE).oneof("kind").build())
                .field(ProtobufField.builder().number(3).name("string_value").type(ProtobufType.STRING).oneof("kind").build())
                .field(ProtobufField.builder().number(4).name("bool_value").type(ProtobufType.BOOL).oneof("kind").build())
                .field(ProtobufField.builder().number(5).name("struct_value").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.Struct").oneof("kind").build())
                .field(ProtobufField.builder().number(6).name("list_value").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.ListValue").oneof("kind").build())
                .build(),
            ProtobufMessage.builder("google.protobuf.ListValue")
                .field(ProtobufField.builder().number(1).name("values").type(ProtobufType.MESSAGE).repeated(true)
                    .typeName("google.protobuf.Value").build())
                .build());
    }
    /**
     * Whether {@code typeName} is one of the nine well-known wrapper type names.
     */
    public static boolean wrapper(
        String typeName)
    {
        return typeName != null && VALUE_TYPES.containsKey(typeName);
    }

    /**
     * The synthetic single-field descriptor for the well-known wrapper type named {@code typeName}: field
     * number 1, named {@code value}, of the type the spec fixes for that wrapper. {@code null} when
     * {@code typeName} is not one of the nine.
     */
    public static ProtobufMessage wrapperMessage(
        String typeName)
    {
        ProtobufType valueType = VALUE_TYPES.get(typeName);
        return valueType == null
            ? null
            : ProtobufMessage.builder(typeName)
                .field(ProtobufField.builder().number(1).name("value").type(valueType).build())
                .build();
    }

    private static ProtobufField scalar(
        int number,
        String name,
        ProtobufType type)
    {
        return ProtobufField.builder().number(number).name(name).type(type).build();
    }

    private ProtobufWellKnownTypes()
    {
    }
}
