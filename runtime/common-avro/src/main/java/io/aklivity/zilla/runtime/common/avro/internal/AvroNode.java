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
package io.aklivity.zilla.runtime.common.avro.internal;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroType;

/**
 * A node in a compiled Avro schema, and the package's {@link AvroType} implementation. Immutable once
 * compiled and freely shared across pipelines. The {@code fieldNames} and {@code children} arrays are the
 * hot-path record-field view (name + type, by position); the parallel {@code fieldAliases} and
 * {@code fieldDefaults} carry the resolution metadata consulted only by {@link #fields()}. The
 * {@link AvroType} accessors build their views per call and are meant for off-the-hot-path inspection.
 */
final class AvroNode implements AvroType
{
    final AvroKind kind;
    final String name;
    final String[] fieldNames;
    final AvroNode[] children;
    final String[] symbols;
    final int size;
    final String logicalType;
    final int precision;
    final int scale;
    final String[] aliases;
    final String[][] fieldAliases;
    final JsonValue[] fieldDefaults;

    private AvroNode(
        AvroKind kind,
        String name,
        String[] fieldNames,
        AvroNode[] children,
        String[] symbols,
        int size,
        String logicalType,
        int precision,
        int scale,
        String[] aliases,
        String[][] fieldAliases,
        JsonValue[] fieldDefaults)
    {
        this.kind = kind;
        this.name = name;
        this.fieldNames = fieldNames;
        this.children = children;
        this.symbols = symbols;
        this.size = size;
        this.logicalType = logicalType;
        this.precision = precision;
        this.scale = scale;
        this.aliases = aliases;
        this.fieldAliases = fieldAliases;
        this.fieldDefaults = fieldDefaults;
    }

    @Override
    public AvroKind kind()
    {
        return kind;
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public String logicalType()
    {
        return logicalType;
    }

    @Override
    public int precision()
    {
        return precision;
    }

    @Override
    public int scale()
    {
        return scale;
    }

    @Override
    public List<String> aliases()
    {
        return aliases == null ? List.of() : List.of(aliases);
    }

    @Override
    public List<AvroField> fields()
    {
        List<AvroField> result = List.of();
        if (kind == AvroKind.RECORD)
        {
            List<AvroField> fields = new ArrayList<>(fieldNames.length);
            for (int i = 0; i < fieldNames.length; i++)
            {
                fields.add(new AvroFieldImpl(fieldNames[i], children[i], fieldAliases[i], fieldDefaults[i]));
            }
            result = List.copyOf(fields);
        }
        return result;
    }

    @Override
    public AvroType items()
    {
        return kind == AvroKind.ARRAY ? children[0] : null;
    }

    @Override
    public AvroType values()
    {
        return kind == AvroKind.MAP ? children[0] : null;
    }

    @Override
    public List<AvroType> branches()
    {
        List<AvroType> result = List.of();
        if (kind == AvroKind.UNION)
        {
            List<AvroType> branches = new ArrayList<>(children.length);
            for (AvroNode child : children)
            {
                branches.add(child);
            }
            result = List.copyOf(branches);
        }
        return result;
    }

    @Override
    public List<String> symbols()
    {
        return kind == AvroKind.ENUM ? List.of(symbols) : List.of();
    }

    @Override
    public int size()
    {
        return size;
    }

    static AvroNode ofPrimitive(
        AvroKind kind,
        String logicalType,
        int precision,
        int scale)
    {
        return new AvroNode(kind, null, null, null, null, 0, logicalType, precision, scale, null, null, null);
    }

    static AvroNode ofRecord(
        String name,
        String[] fieldNames,
        AvroNode[] fieldTypes,
        String[] aliases,
        String[][] fieldAliases,
        JsonValue[] fieldDefaults)
    {
        return new AvroNode(AvroKind.RECORD, name, fieldNames, fieldTypes, null, 0, null, 0, 0,
            aliases, fieldAliases, fieldDefaults);
    }

    static AvroNode ofArray(
        AvroNode elementType)
    {
        return new AvroNode(AvroKind.ARRAY, null, null, new AvroNode[] { elementType }, null, 0, null, 0, 0,
            null, null, null);
    }

    static AvroNode ofMap(
        AvroNode valueType)
    {
        return new AvroNode(AvroKind.MAP, null, null, new AvroNode[] { valueType }, null, 0, null, 0, 0,
            null, null, null);
    }

    static AvroNode ofUnion(
        AvroNode[] branches)
    {
        return new AvroNode(AvroKind.UNION, null, null, branches, null, 0, null, 0, 0, null, null, null);
    }

    static AvroNode ofEnum(
        String name,
        String[] symbols,
        String[] aliases)
    {
        return new AvroNode(AvroKind.ENUM, name, null, null, symbols, 0, null, 0, 0, aliases, null, null);
    }

    static AvroNode ofFixed(
        String name,
        int size,
        String logicalType,
        int precision,
        int scale,
        String[] aliases)
    {
        return new AvroNode(AvroKind.FIXED, name, null, null, null, size, logicalType, precision, scale,
            aliases, null, null);
    }
}
