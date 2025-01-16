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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiPath;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiPathView
{
    public final OpenapiView specification;
    public final String path;
    public final Map<String, OpenapiOperationView> methods;

    private static final Map<String, Function<OpenapiPath, OpenapiOperation>> METHOD_ACCESSORS;

    static
    {
        Map<String, Function<OpenapiPath, OpenapiOperation>> accessors = new LinkedHashMap<>();

        try
        {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (String method : List.of("GET", "PUT", "POST", "DELETE", "OPTIONS", "HEAD", "PATCH", "TRACE"))
            {
                String field = method.toLowerCase();
                VarHandle handle = lookup.findVarHandle(OpenapiPath.class, field, OpenapiOperation.class);

                accessors.put(method, p -> OpenapiOperation.class.cast(handle.get(p)));
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        METHOD_ACCESSORS = unmodifiableMap(accessors);
    }

    OpenapiPathView(
        OpenapiView specification,
        LongSupplier supplyCompositeId,
        List<OpenapiServerConfig> configs,
        OpenapiResolver resolver,
        String path,
        OpenapiPath model)
    {
        this.specification = specification;
        this.path = path;
        this.methods = METHOD_ACCESSORS.entrySet().stream()
            .filter(e -> e.getValue().apply(model) != null)
            .collect(Collectors.toMap(Map.Entry::getKey, e ->
                new OpenapiOperationView(specification, supplyCompositeId.getAsLong(),
                        configs, resolver, e.getKey(), path, e.getValue().apply(model))));
    }
}
