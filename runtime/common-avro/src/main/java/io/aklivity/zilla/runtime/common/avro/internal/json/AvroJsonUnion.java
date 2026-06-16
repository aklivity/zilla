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
package io.aklivity.zilla.runtime.common.avro.internal.json;

import java.util.List;

import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroType;

/**
 * The Avro JSON union encoding shared by the JSON ↔ Avro adapters: a union value is {@code null} for its null
 * branch, or the single-entry object {@code {"<branch>": value}} for any other branch, where {@code <branch>}
 * is the primitive type name, the declared name of a {@code record}/{@code enum}/{@code fixed}, or
 * {@code "array"}/{@code "map"}. Resolution allocates nothing on the hot path — callers supply the per-node
 * cached branch list.
 */
final class AvroJsonUnion
{
    private AvroJsonUnion()
    {
    }

    /**
     * The JSON object key the Avro JSON encoding uses for {@code branch} of a union — the primitive type
     * name, the declared name of a named type, or {@code "array"} / {@code "map"}.
     */
    static String branchName(
        AvroType branch)
    {
        AvroKind kind = branch.kind();
        String name;
        switch (kind)
        {
        case NULL:
            name = "null";
            break;
        case BOOLEAN:
            name = "boolean";
            break;
        case INT:
            name = "int";
            break;
        case LONG:
            name = "long";
            break;
        case FLOAT:
            name = "float";
            break;
        case DOUBLE:
            name = "double";
            break;
        case BYTES:
            name = "bytes";
            break;
        case STRING:
            name = "string";
            break;
        case ARRAY:
            name = "array";
            break;
        case MAP:
            name = "map";
            break;
        default:
            name = branch.name();
            break;
        }
        return name;
    }

    /**
     * The index of the {@code branches} entry whose key matches {@code name} (the Avro JSON union
     * discriminator), or {@code -1} when no branch matches. The list is supplied by the caller (cached per
     * schema node) so resolution allocates nothing on the hot path.
     */
    static int branchIndex(
        List<AvroType> branches,
        CharSequence name)
    {
        int index = -1;
        int count = branches.size();
        for (int i = 0; index < 0 && i < count; i++)
        {
            if (branchName(branches.get(i)).contentEquals(name))
            {
                index = i;
            }
        }
        return index;
    }

    /**
     * The index of the {@code branches} entry whose kind is {@code null}, or {@code -1} when there is no
     * null branch.
     */
    static int nullBranchIndex(
        List<AvroType> branches)
    {
        int index = -1;
        int count = branches.size();
        for (int i = 0; index < 0 && i < count; i++)
        {
            if (branches.get(i).kind() == AvroKind.NULL)
            {
                index = i;
            }
        }
        return index;
    }
}
