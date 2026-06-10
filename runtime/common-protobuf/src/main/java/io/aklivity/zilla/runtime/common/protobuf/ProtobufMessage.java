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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable Protobuf message descriptor: its fields indexed by number, proto JSON name, and
 * proto name, plus a flag marking a synthetic map-entry message so a repeated reference to it
 * renders as a JSON object rather than an array.
 */
public final class ProtobufMessage
{
    private final String name;
    private final boolean mapEntry;
    private final List<ProtobufField> fields;
    private final Map<Integer, ProtobufField> fieldByNumber;
    private final Map<String, ProtobufField> fieldByJsonName;

    private ProtobufMessage(
        String name,
        boolean mapEntry,
        List<ProtobufField> fields)
    {
        this.name = name;
        this.mapEntry = mapEntry;
        this.fields = Collections.unmodifiableList(fields);

        Map<Integer, ProtobufField> byNumber = new LinkedHashMap<>();
        Map<String, ProtobufField> byJsonName = new LinkedHashMap<>();
        for (ProtobufField field : fields)
        {
            byNumber.put(field.number(), field);
            byJsonName.put(field.jsonName(), field);
            byJsonName.putIfAbsent(field.name(), field);
        }
        this.fieldByNumber = byNumber;
        this.fieldByJsonName = byJsonName;
    }

    public String name()
    {
        return name;
    }

    public boolean mapEntry()
    {
        return mapEntry;
    }

    public List<ProtobufField> fields()
    {
        return fields;
    }

    public ProtobufField field(
        int number)
    {
        return fieldByNumber.get(number);
    }

    public ProtobufField field(
        String jsonNameOrName)
    {
        return fieldByJsonName.get(jsonNameOrName);
    }

    public ProtobufField mapKey()
    {
        return fieldByNumber.get(1);
    }

    public ProtobufField mapValue()
    {
        return fieldByNumber.get(2);
    }

    public static Builder builder(
        String name)
    {
        return new Builder(name);
    }

    public static final class Builder
    {
        private final String name;
        private final List<ProtobufField> fields;
        private boolean mapEntry;

        private Builder(
            String name)
        {
            this.name = name;
            this.fields = new ArrayList<>();
        }

        public Builder mapEntry(
            boolean mapEntry)
        {
            this.mapEntry = mapEntry;
            return this;
        }

        public Builder field(
            ProtobufField field)
        {
            fields.add(field);
            return this;
        }

        public ProtobufMessage build()
        {
            return new ProtobufMessage(name, mapEntry, fields);
        }
    }
}
