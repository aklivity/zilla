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

import java.util.Objects;

/**
 * An immutable Protobuf field descriptor: its number, declared {@link ProtobufType}, cardinality,
 * proto3 JSON name, and, for composite fields, the full name of the referenced message or enum.
 */
public final class ProtobufField
{
    private final int number;
    private final String name;
    private final String jsonName;
    private final ProtobufType type;
    private final boolean repeated;
    private final boolean required;
    private final boolean packed;
    private final boolean proto3Optional;
    private final String typeName;
    private final String oneofName;

    private ProtobufMessage message;
    private ProtobufEnum enumeration;

    private ProtobufField(
        int number,
        String name,
        String jsonName,
        ProtobufType type,
        boolean repeated,
        boolean required,
        boolean packed,
        boolean proto3Optional,
        String typeName,
        String oneofName)
    {
        this.number = number;
        this.name = name;
        this.jsonName = jsonName;
        this.type = type;
        this.repeated = repeated;
        this.required = required;
        this.packed = packed;
        this.proto3Optional = proto3Optional;
        this.typeName = typeName;
        this.oneofName = oneofName;
    }

    public int number()
    {
        return number;
    }

    public String name()
    {
        return name;
    }

    public String jsonName()
    {
        return jsonName;
    }

    public ProtobufType type()
    {
        return type;
    }

    public boolean repeated()
    {
        return repeated;
    }

    /**
     * A proto2 {@code required} field. proto3 has no required fields, so this is always false there.
     */
    public boolean required()
    {
        return required;
    }

    public boolean packed()
    {
        return packed;
    }

    public boolean proto3Optional()
    {
        return proto3Optional;
    }

    public String typeName()
    {
        return typeName;
    }

    public String oneofName()
    {
        return oneofName;
    }

    public boolean composite()
    {
        return type == ProtobufType.MESSAGE || type == ProtobufType.GROUP;
    }

    /**
     * The nested message descriptor a composite (message or group) field references, linked when the
     * owning message is assembled into a {@link ProtobufSchema}. {@code null} for scalar and enum fields,
     * and for a field not (yet) part of a built schema. Lets a consumer walk the descriptor graph
     * directly — {@code message().field(n).message()} — without a name lookup against the schema.
     */
    public ProtobufMessage message()
    {
        return message;
    }

    /**
     * The enum descriptor an {@code enum} field references, linked when the owning message is assembled
     * into a {@link ProtobufSchema}; {@code null} otherwise.
     */
    public ProtobufEnum enumeration()
    {
        return enumeration;
    }

    void resolve(
        ProtobufMessage message)
    {
        this.message = message;
    }

    void resolve(
        ProtobufEnum enumeration)
    {
        this.enumeration = enumeration;
    }

    /**
     * Derives the proto3 JSON name (lowerCamelCase) from a proto field name (snake_case).
     */
    public static String toJsonName(
        String name)
    {
        StringBuilder json = new StringBuilder(name.length());
        boolean upper = false;
        for (int i = 0; i < name.length(); i++)
        {
            char ch = name.charAt(i);
            if (ch == '_')
            {
                upper = true;
            }
            else if (upper)
            {
                json.append(Character.toUpperCase(ch));
                upper = false;
            }
            else
            {
                json.append(ch);
            }
        }
        return json.toString();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private int number;
        private String name;
        private String jsonName;
        private ProtobufType type;
        private boolean repeated;
        private boolean required;
        private Boolean packed;
        private boolean proto3Optional;
        private String typeName;
        private String oneofName;

        public Builder number(
            int number)
        {
            this.number = number;
            return this;
        }

        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder jsonName(
            String jsonName)
        {
            this.jsonName = jsonName;
            return this;
        }

        public Builder type(
            ProtobufType type)
        {
            this.type = type;
            return this;
        }

        public Builder repeated(
            boolean repeated)
        {
            this.repeated = repeated;
            return this;
        }

        public Builder required(
            boolean required)
        {
            this.required = required;
            return this;
        }

        public Builder packed(
            boolean packed)
        {
            this.packed = packed;
            return this;
        }

        public Builder proto3Optional(
            boolean proto3Optional)
        {
            this.proto3Optional = proto3Optional;
            return this;
        }

        public Builder typeName(
            String typeName)
        {
            this.typeName = typeName;
            return this;
        }

        public Builder oneof(
            String oneofName)
        {
            this.oneofName = oneofName;
            return this;
        }

        public ProtobufField build()
        {
            Objects.requireNonNull(name, "field name");
            Objects.requireNonNull(type, "field type");
            String resolvedJsonName = jsonName != null ? jsonName : toJsonName(name);
            boolean resolvedPacked = packed != null ? packed : repeated && type.packable();
            return new ProtobufField(number, name, resolvedJsonName, type, repeated, required, resolvedPacked,
                proto3Optional, typeName, oneofName);
        }
    }
}
