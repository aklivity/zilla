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
package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

public final class RisingwavePgsqlTypeMapping
{
    private static final Map<String, String> TYPE_MAPPINGS = new Object2ObjectHashMap<>();

    static
    {
        TYPE_MAPPINGS.put("character varying", "VARCHAR");
        TYPE_MAPPINGS.put("integer", "INT");
        TYPE_MAPPINGS.put("boolean", "BOOL");
        TYPE_MAPPINGS.put("character", "CHAR");
        TYPE_MAPPINGS.put("timestamp without time zone", "TIMESTAMP");
        TYPE_MAPPINGS.put("timestamp with time zone", "TIMESTAMPZ");
        TYPE_MAPPINGS.put("double precision", "DOUBLE");
        TYPE_MAPPINGS.put("numeric", "NUMERIC");
    }

    private RisingwavePgsqlTypeMapping()
    {
    }

    public static String typeName(
        String type)
    {
        return TYPE_MAPPINGS.get(type);
    }
}
