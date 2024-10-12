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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiPathItem;

public final class OpenapiPathView
{
    public final Map<String, OpenapiOperation> methods;

    OpenapiPathView(
        OpenapiPathItem pathItem)
    {
        Map<String, OpenapiOperation> methods = new LinkedHashMap<>();
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

    private static <K, V> void putIfNotNull(
        Map<K, V> map,
        K key,
        V value)
    {
        if (value != null)
        {
            map.put(key, value);
        }
    }
}
