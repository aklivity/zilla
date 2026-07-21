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
package io.aklivity.zilla.runtime.common.openapi.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenapiParserFactory
{
    private final Map<OpenapiExtension.Scope, Map<String, Class<?>>> extensionTypes;
    private final Map<OpenapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes;

    public OpenapiParserFactory()
    {
        this.extensionTypes = new LinkedHashMap<>();
        this.prefixExtensionTypes = new LinkedHashMap<>();
    }

    public OpenapiParserFactory withExtension(
        OpenapiExtension<?> extension)
    {
        String name = extension.name();
        if (name.endsWith("*"))
        {
            prefixExtensionTypes.computeIfAbsent(extension.scope(), s -> new LinkedHashMap<>())
                .put(name, extension.type());
        }
        else
        {
            extensionTypes.computeIfAbsent(extension.scope(), s -> new LinkedHashMap<>())
                .put(name, extension.type());
        }
        return this;
    }

    public OpenapiParser createParser()
    {
        return new OpenapiParser(extensionTypes, prefixExtensionTypes);
    }
}
