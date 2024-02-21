/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.PathItem;

public final class OpenApiPathView
{
    private final Map<String, OpenApiOperation> methods;

    public Map<String, OpenApiOperation> methods()
    {
        return methods;
    }

    public static OpenApiPathView of(
        PathItem pathItem)
    {
        return new OpenApiPathView(pathItem);
    }

    private OpenApiPathView(
        PathItem pathItem)
    {
        Map<String, OpenApiOperation> methods = new LinkedHashMap<>();
        putIfNotNull(methods, "GET", pathItem.get);
        putIfNotNull(methods, "PUT", pathItem.put);
        putIfNotNull(methods, "POST", pathItem.post);
        putIfNotNull(methods, "DELETE", pathItem.delete);
        putIfNotNull(methods, "OPTIONS", pathItem.options);
        putIfNotNull(methods, "HEAD", pathItem.head);
        putIfNotNull(methods, "PATCH", pathItem.patch);
        putIfNotNull(methods, "TRACE", pathItem.trace);
        this.methods = unmodifiableMap(methods);
    }

    private static void putIfNotNull(
        Map<String, OpenApiOperation> methods,
        String method,
        OpenApiOperation operation)
    {
        if (operation != null)
        {
            methods.put(method, operation);
        }
    }
}
