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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
    private final List<ProtobufField> sortedFields;
    private final List<ProtobufField> requiredFields;
    private final Map<Integer, ProtobufField> fieldByNumber;
    private final Map<String, ProtobufField> fieldByJsonName;
    private final Map<String, ProtobufConstant> options;

    private ProtobufMessage(
        String name,
        boolean mapEntry,
        List<ProtobufField> fields,
        Map<String, ProtobufConstant> options)
    {
        this.name = name;
        this.mapEntry = mapEntry;
        this.fields = Collections.unmodifiableList(fields);
        this.options = options;

        List<ProtobufField> sorted = new ArrayList<>(fields);
        sorted.sort(Comparator.comparingInt(ProtobufField::number));
        this.sortedFields = Collections.unmodifiableList(sorted);

        List<ProtobufField> required = new ArrayList<>();
        for (ProtobufField field : fields)
        {
            if (field.required())
            {
                required.add(field);
            }
        }
        this.requiredFields = Collections.unmodifiableList(required);

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

    /**
     * Fields in ascending field-number order, as required for canonical wire serialization.
     */
    public List<ProtobufField> sortedFields()
    {
        return sortedFields;
    }

    /**
     * The proto2 {@code required} fields, in declaration order — empty for proto3 messages.
     */
    public List<ProtobufField> requiredFields()
    {
        return requiredFields;
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

    /**
     * The value of a message option declared in this message's {@code option ...;} statements, read
     * from this message only. A field or nested message option is not visible here, and an option
     * declared here is not inherited by either. {@code null} when this message declares no such option.
     */
    public ProtobufConstant option(
        String name)
    {
        return options != null ? options.get(name) : null;
    }

    Map<String, ProtobufConstant> rawOptions()
    {
        return options;
    }

    /**
     * Resolves a field by its proto3 json name or proto name from a non-owning {@link CharSequence} (e.g. a
     * parser's key view), without materializing a {@code String} for the lookup. Scans in declaration order,
     * preferring a json-name match; returns {@code null} when no field matches.
     */
    public ProtobufField field(
        CharSequence jsonNameOrName)
    {
        ProtobufField match = null;
        for (int i = 0; match == null && i < fields.size(); i++)
        {
            ProtobufField field = fields.get(i);
            if (field.jsonName().contentEquals(jsonNameOrName) || field.name().contentEquals(jsonNameOrName))
            {
                match = field;
            }
        }
        return match;
    }

    public ProtobufField mapKey()
    {
        return fieldByNumber.get(1);
    }

    public ProtobufField mapValue()
    {
        return fieldByNumber.get(2);
    }

    /**
     * The paths of every {@link ProtobufField} reachable from this message — through composite
     * (message-typed) fields — for which {@code filter} returns {@code true}, in declaration order.
     * A field's own match does not stop descent into its message: a matching field nested beneath an
     * already-matching ancestor is still reported, independently, at its own path. A {@code repeated}
     * composite field contributes a synthetic {@code -} wildcard segment before its message's own
     * fields, matching the array-of-objects path convention used elsewhere for Avro and JSON Schema
     * (a {@code map} field is a repeated synthetic entry message with named {@code key}/{@code value}
     * fields, so its own value type is reached one segment deeper, at {@code .../value/...}, rather
     * than directly at {@code -}). The one thing that does stop descent is a recursive message
     * revisiting a message already on the current path; that message's fields are not re-examined a
     * second time along the same descent.
     */
    public List<String> matchingPaths(
        Predicate<ProtobufField> filter)
    {
        Set<String> pointers = new LinkedHashSet<>();
        collectPaths("", filter, new HashSet<>(), pointers);
        return new ArrayList<>(pointers);
    }

    // identity-based (ProtobufMessage has no equals/hashCode override): guards against a recursive
    // message revisiting itself along the same descent, not against revisiting via a different path
    private void collectPaths(
        String pointer,
        Predicate<ProtobufField> filter,
        Set<ProtobufMessage> visiting,
        Set<String> pointers)
    {
        if (visiting.add(this))
        {
            for (ProtobufField field : fields)
            {
                String fieldPointer = pointer + "/" + field.name();
                if (filter.test(field))
                {
                    pointers.add(fieldPointer);
                }

                ProtobufMessage nested = field.message();
                if (nested != null)
                {
                    nested.collectPaths(field.repeated() ? fieldPointer + "/-" : fieldPointer, filter, visiting,
                        pointers);
                }
            }
            visiting.remove(this);
        }
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
        private Map<String, ProtobufConstant> options;

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

        public Builder options(
            Map<String, ProtobufConstant> options)
        {
            this.options = options;
            return this;
        }

        public ProtobufMessage build()
        {
            return new ProtobufMessage(name, mapEntry, fields, options);
        }
    }
}
