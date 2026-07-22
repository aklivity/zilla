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
package io.aklivity.zilla.runtime.common.openapi.view;

import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.openapi.model.OpenapiMediaType;
import io.aklivity.zilla.runtime.common.openapi.model.resolver.OpenapiResolver;

public class OpenapiMediaTypeView
{
    public final String name;
    public final OpenapiSchemaView schema;
    public final OpenapiEncodingView encoding;

    private final Map<String, Object> extensions;

    OpenapiMediaTypeView(
        OpenapiResolver resolver,
        String name,
        OpenapiMediaType model)
    {
        this.name = name;
        this.schema = model.schema != null
            ? new OpenapiSchemaView(resolver, model.schema)
            : null;
        this.encoding = model.encoding != null
            ? new OpenapiEncodingView(resolver, model.encoding)
            : null;
        this.extensions = model.extensions;
    }

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }
}
