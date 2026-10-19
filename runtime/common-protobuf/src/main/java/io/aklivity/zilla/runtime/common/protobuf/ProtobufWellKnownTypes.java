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

import java.util.Map;
import java.util.Set;

/**
 * The nine {@code google.protobuf} well-known wrapper message types — {@code StringValue}, {@code BytesValue},
 * {@code BoolValue}, {@code Int32Value}, {@code UInt32Value}, {@code Int64Value}, {@code UInt64Value},
 * {@code FloatValue}, {@code DoubleValue}. Each has a wire shape fixed by the protobuf spec itself — always
 * exactly one field, number 1 named {@code value}, of a fixed corresponding scalar type — so recognizing one
 * by its fully-qualified name and resolving its shape is a pure lookup, never a schema-import concern; a
 * schema referencing one of these types by name need not declare or import it.
 */
public final class ProtobufWellKnownTypes
{
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

    /**
     * The fully-qualified names of all nine well-known wrapper types.
     */
    public static Set<String> names()
    {
        return VALUE_TYPES.keySet();
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

    private ProtobufWellKnownTypes()
    {
    }
}
