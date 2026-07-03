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
package io.aklivity.zilla.runtime.common.openapi.model;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

public class OpenapiExtension
{
    private static final Jsonb JSONB = JsonbBuilder.create();

    public JsonValue value;

    // eagerly bound when the caller registered a type for this extension's name via
    // OpenapiParserFactory.withExtension(name, type); null otherwise, in which case the
    // view layer binds `value` to the caller-requested type lazily, on first access
    public Object bound;

    public static Map<String, OpenapiExtension> capture(
        JsonObject object,
        Map<String, Class<?>> extensionTypes)
    {
        Map<String, OpenapiExtension> extensions = null;

        for (String name : object.keySet())
        {
            if (name.startsWith("x-"))
            {
                if (extensions == null)
                {
                    extensions = new LinkedHashMap<>();
                }

                OpenapiExtension extension = new OpenapiExtension();
                extension.value = object.get(name);

                Class<?> type = extensionTypes.get(name);
                if (type != null)
                {
                    extension.bound = JSONB.fromJson(extension.value.toString(), type);
                }

                extensions.put(name, extension);
            }
        }

        return extensions;
    }
}
