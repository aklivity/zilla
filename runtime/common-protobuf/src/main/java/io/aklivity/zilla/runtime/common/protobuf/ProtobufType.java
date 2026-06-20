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
package io.aklivity.zilla.runtime.common.protobuf;

import static io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType.I32;
import static io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType.I64;
import static io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType.LEN;
import static io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType.SGROUP;
import static io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType.VARINT;

/**
 * The declared scalar and composite field types of the Protobuf type system, each bound to the
 * {@link ProtobufWireType} it travels as on the wire. Ordinals follow
 * {@code FieldDescriptorProto.Type} so descriptor builders can map by proto type number.
 */
public enum ProtobufType
{
    DOUBLE(1, I64),
    FLOAT(2, I32),
    INT64(3, VARINT),
    UINT64(4, VARINT),
    INT32(5, VARINT),
    FIXED64(6, I64),
    FIXED32(7, I32),
    BOOL(8, VARINT),
    STRING(9, LEN),
    GROUP(10, SGROUP),
    MESSAGE(11, LEN),
    BYTES(12, LEN),
    UINT32(13, VARINT),
    ENUM(14, VARINT),
    SFIXED32(15, I32),
    SFIXED64(16, I64),
    SINT32(17, VARINT),
    SINT64(18, VARINT);

    private static final ProtobufType[] BY_NUMBER = new ProtobufType[19];

    static
    {
        for (ProtobufType type : values())
        {
            BY_NUMBER[type.number] = type;
        }
    }

    private final int number;
    private final ProtobufWireType wireType;

    ProtobufType(
        int number,
        ProtobufWireType wireType)
    {
        this.number = number;
        this.wireType = wireType;
    }

    public int number()
    {
        return number;
    }

    public ProtobufWireType wireType()
    {
        return wireType;
    }

    /**
     * A scalar numeric or boolean field that may travel packed inside a single length-delimited
     * region when repeated.
     */
    public boolean packable()
    {
        return wireType != LEN && wireType != SGROUP && this != GROUP && this != MESSAGE;
    }

    public boolean zigzag()
    {
        return this == SINT32 || this == SINT64;
    }

    public boolean unsigned()
    {
        return this == UINT32 || this == UINT64 || this == FIXED32 || this == FIXED64;
    }

    public static ProtobufType ofNumber(
        int number)
    {
        ProtobufType type = number >= 0 && number < BY_NUMBER.length ? BY_NUMBER[number] : null;
        if (type == null)
        {
            throw new ProtobufException("invalid field type number " + number);
        }
        return type;
    }
}
