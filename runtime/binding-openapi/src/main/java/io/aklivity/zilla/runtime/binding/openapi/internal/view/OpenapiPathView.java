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
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiPath;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiPathView
{
    public final OpenapiView specification;
    public final String path;
    public final Map<String, OpenapiOperationView> methods;

    OpenapiPathView(
        OpenapiView specification,
        List<OpenapiServerConfig> configs,
        OpenapiResolver resolver,
        String path,
        OpenapiPath model)
    {
        this.specification = specification;
        this.path = path;

        Map<String, OpenapiOperationView> methods = new LinkedHashMap<>();
        putIfModelNotNull(methods, this, configs, resolver, "GET", model.get);
        putIfModelNotNull(methods, this, configs, resolver, "PUT", model.put);
        putIfModelNotNull(methods, this, configs, resolver, "POST", model.post);
        putIfModelNotNull(methods, this, configs, resolver, "DELETE", model.delete);
        putIfModelNotNull(methods, this, configs, resolver, "OPTIONS", model.options);
        putIfModelNotNull(methods, this, configs, resolver, "HEAD", model.head);
        putIfModelNotNull(methods, this, configs, resolver, "PATCH", model.patch);
        putIfModelNotNull(methods, this, configs, resolver, "TRACE", model.trace);
        this.methods = unmodifiableMap(methods);
    }

    private static <K, V> void putIfModelNotNull(
        Map<String, OpenapiOperationView> methods,
        OpenapiPathView path,
        List<OpenapiServerConfig> configs,
        OpenapiResolver resolver,
        String name,
        OpenapiOperation model)
    {
        if (model != null)
        {
            methods.put(name, new OpenapiOperationView(path, configs, resolver, name, model));
        }
    }
}
