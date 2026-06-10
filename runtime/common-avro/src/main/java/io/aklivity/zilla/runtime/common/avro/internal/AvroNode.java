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
package io.aklivity.zilla.runtime.common.avro.internal;

/**
 * A node in a compiled Avro schema. Immutable once compiled; shared across pipelines on a worker.
 * The {@code children} array holds record field types, union branch types, the array element type
 * (single entry), or the map value type (single entry), depending on {@link #kind}.
 */
final class AvroNode
{
    final AvroKind kind;
    final String[] fieldNames;
    final AvroNode[] children;
    final String[] symbols;
    final int size;
    final String logicalType;

    private AvroNode(
        AvroKind kind,
        String[] fieldNames,
        AvroNode[] children,
        String[] symbols,
        int size,
        String logicalType)
    {
        this.kind = kind;
        this.fieldNames = fieldNames;
        this.children = children;
        this.symbols = symbols;
        this.size = size;
        this.logicalType = logicalType;
    }

    static AvroNode ofPrimitive(
        AvroKind kind,
        String logicalType)
    {
        return new AvroNode(kind, null, null, null, 0, logicalType);
    }

    static AvroNode ofRecord(
        String[] fieldNames,
        AvroNode[] fieldTypes)
    {
        return new AvroNode(AvroKind.RECORD, fieldNames, fieldTypes, null, 0, null);
    }

    static AvroNode ofArray(
        AvroNode elementType)
    {
        return new AvroNode(AvroKind.ARRAY, null, new AvroNode[] { elementType }, null, 0, null);
    }

    static AvroNode ofMap(
        AvroNode valueType)
    {
        return new AvroNode(AvroKind.MAP, null, new AvroNode[] { valueType }, null, 0, null);
    }

    static AvroNode ofUnion(
        AvroNode[] branches)
    {
        return new AvroNode(AvroKind.UNION, null, branches, null, 0, null);
    }

    static AvroNode ofEnum(
        String[] symbols)
    {
        return new AvroNode(AvroKind.ENUM, null, null, symbols, 0, null);
    }

    static AvroNode ofFixed(
        int size,
        String logicalType)
    {
        return new AvroNode(AvroKind.FIXED, null, null, null, size, logicalType);
    }
}
