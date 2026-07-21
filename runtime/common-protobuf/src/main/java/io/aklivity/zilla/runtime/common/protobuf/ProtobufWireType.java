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

/**
 * The Protobuf wire types as encoded in the low three bits of a field tag.
 */
public enum ProtobufWireType
{
    VARINT(0),
    I64(1),
    LEN(2),
    SGROUP(3),
    EGROUP(4),
    I32(5);

    private static final ProtobufWireType[] VALUES = new ProtobufWireType[8];

    static
    {
        for (ProtobufWireType type : values())
        {
            VALUES[type.code] = type;
        }
    }

    private final int code;

    ProtobufWireType(
        int code)
    {
        this.code = code;
    }

    public int code()
    {
        return code;
    }

    public static ProtobufWireType of(
        int code)
    {
        ProtobufWireType type = code >= 0 && code < VALUES.length ? VALUES[code] : null;
        if (type == null)
        {
            throw new ProtobufException("invalid wire type " + code);
        }
        return type;
    }
}
