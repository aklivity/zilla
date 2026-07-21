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
package io.aklivity.zilla.runtime.common.json.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a JSON Schema (draft-07 subset) into the set of RFC 6901 JSON Pointers to retain
 * when projecting an instance with the projector transform — the union of the paths
 * declared across all branches of the schema.
 * <p>
 * {@code properties} contribute child pointers; {@code items} (single schema) contributes a
 * {@code -} array-index wildcard; {@code allOf}/{@code anyOf}/{@code oneOf}/{@code if}/{@code
 * then}/{@code else} are unioned at the same pointer. A schema with no declared structure is a
 * retained leaf (its whole subtree is kept). Only leaf pointers are emitted; the projector's
 * prefix logic descends the ancestors. {@code $ref}, {@code patternProperties} and tuple {@code
 * items} are not yet expanded — a node bearing only those is treated as a retained leaf.
 */
final class JsonSchemaPaths
{
    private JsonSchemaPaths()
    {
    }

    public static List<String> retained(
        String schema)
    {
        Set<String> pointers = new LinkedHashSet<>();
        collect(JsonNode.parse(schema), "", pointers);
        return new ArrayList<>(pointers);
    }

    private static void collect(
        JsonNode schema,
        String pointer,
        Set<String> pointers)
    {
        switch (schema.kind())
        {
        case OBJECT:
            collectObject(schema, pointer, pointers);
            break;
        case TRUE:
            pointers.add(pointer);
            break;
        default:
            break;
        }
    }

    private static void collectObject(
        JsonNode schema,
        String pointer,
        Set<String> pointers)
    {
        boolean structured = false;

        if (schema.has("properties"))
        {
            structured = true;
            for (Map.Entry<String, JsonNode> entry : schema.get("properties").members().entrySet())
            {
                collect(entry.getValue(), pointer + "/" + escape(entry.getKey()), pointers);
            }
        }

        JsonNode items = schema.get("items");
        if (items != null && !items.isArray())
        {
            structured = true;
            collect(items, pointer + "/-", pointers);
        }

        structured |= collectBranches(schema, "allOf", pointer, pointers);
        structured |= collectBranches(schema, "anyOf", pointer, pointers);
        structured |= collectBranches(schema, "oneOf", pointer, pointers);
        structured |= collectBranch(schema, "if", pointer, pointers);
        structured |= collectBranch(schema, "then", pointer, pointers);
        structured |= collectBranch(schema, "else", pointer, pointers);

        if (!structured)
        {
            pointers.add(pointer);
        }
    }

    private static boolean collectBranches(
        JsonNode schema,
        String keyword,
        String pointer,
        Set<String> pointers)
    {
        boolean present = schema.has(keyword) && schema.get(keyword).isArray();
        if (present)
        {
            for (JsonNode branch : schema.get(keyword).elements())
            {
                collect(branch, pointer, pointers);
            }
        }
        return present;
    }

    private static boolean collectBranch(
        JsonNode schema,
        String keyword,
        String pointer,
        Set<String> pointers)
    {
        boolean present = schema.has(keyword);
        if (present)
        {
            collect(schema.get(keyword), pointer, pointers);
        }
        return present;
    }

    private static String escape(
        String name)
    {
        return name.replace("~", "~0").replace("/", "~1");
    }
}
