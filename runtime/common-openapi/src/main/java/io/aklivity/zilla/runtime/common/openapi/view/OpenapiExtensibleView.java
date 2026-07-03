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
package io.aklivity.zilla.runtime.common.openapi.view;

import java.util.Map;
import java.util.Optional;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.common.openapi.model.OpenapiExtension;

abstract class OpenapiExtensibleView
{
    private static final Jsonb JSONB = JsonbBuilder.create();

    private final Map<String, OpenapiExtension> extensions;

    OpenapiExtensibleView(
        Map<String, OpenapiExtension> extensions)
    {
        this.extensions = extensions;
    }

    public final boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public final <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        T value = null;

        if (extensions != null && extensions.containsKey(name))
        {
            OpenapiExtension extension = extensions.get(name);
            value = extension.bound != null ? type.cast(extension.bound) : JSONB.fromJson(extension.value.toString(), type);
        }

        return Optional.ofNullable(value);
    }
}
